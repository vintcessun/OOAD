package com.xmu.rbac.ddd.domain;

import com.xmu.rbac.contract.CourseRoleCodes;
import java.util.*;

/** 领域授权专家：并集去重、解释来源、有效角色码及非超级管理员授权上界。 */
public final class AuthorizationPolicy {
    public record Source(
            String permissionCode,
            Permission.Kind permissionKind,
            String roleCode,
            boolean inherited,
            List<String> path) {}

    public record Effective(
            List<Role> directRoles,
            List<Role> effectiveRoles,
            List<Permission> permissions,
            List<Source> sources) {
        public Set<String> roleCodes() {
            Set<String> result = new TreeSet<>();
            effectiveRoles.forEach(role -> result.add(role.code()));
            return Set.copyOf(result);
        }

        public Set<String> courseRoleCodes() {
            return CourseRoleCodes.retainFrom(roleCodes());
        }

        public boolean has(String code, Permission.Kind kind) {
            return permissions.stream().anyMatch(p -> p.kind() == kind && p.code().equals(code));
        }

        public boolean superAdministrator() {
            return effectiveRoles.stream()
                    .anyMatch(
                            r ->
                                    r.systemCode().equals("rbac-center")
                                            && r.code().equals("SUPER_ADMIN"));
        }
    }

    public Effective calculate(
            String systemCode,
            Set<Long> direct,
            RoleHierarchy graph,
            Collection<Permission> permissions) {
        Map<Long, Role> roles = new LinkedHashMap<>();
        for (Role r : graph.roles()) if (r.systemCode().equals(systemCode)) roles.put(r.id(), r);
        Map<Long, Permission> valid = new HashMap<>();
        for (Permission p : permissions)
            if (p.enabled() && p.systemCode().equals(systemCode)) valid.put(p.id(), p);
        Map<Long, Permission> union = new TreeMap<>();
        Map<Long, Role> effective = new TreeMap<>();
        List<Source> sources = new ArrayList<>();
        Set<Long> safeDirect = new HashSet<>(direct);
        safeDirect.retainAll(roles.keySet());
        for (List<Long> path : graph.paths(safeDirect)) {
            Role role = roles.get(path.getLast());
            if (role == null) continue;
            effective.put(role.id(), role);
            for (Long id : new TreeSet<>(role.permissionIds())) {
                Permission p = valid.get(id);
                if (p == null) continue;
                union.put(id, p);
                sources.add(
                        new Source(
                                p.code(),
                                p.kind(),
                                role.code(),
                                path.size() > 1,
                                path.stream().map(roles::get).map(Role::code).toList()));
            }
        }
        return new Effective(
                roles.values().stream()
                        .filter(r -> safeDirect.contains(r.id()) && r.enabled())
                        .toList(),
                List.copyOf(effective.values()),
                List.copyOf(union.values()),
                List.copyOf(sources));
    }

    public void requireWithin(Effective actor, Effective proposed, boolean superAdmin, Set<String> protectedRoleCodes) {
        if (superAdmin) return;
        DomainException.require(
                proposed.directRoles().stream().noneMatch(Role::superAdministratorCapability)
                        && proposed.effectiveRoles().stream()
                                .noneMatch(Role::superAdministratorCapability),
                403,
                "GRANT_EXCEEDS_ACTOR");
        Set<Long> available = new HashSet<>();
        for (Permission p : actor.permissions()) available.add(p.id());
        DomainException.require(
                proposed.permissions().stream().allMatch(p -> available.contains(p.id()))
                        && proposed.roleCodes().stream()
                                .filter(protectedRoleCodes::contains)
                                .allMatch(actor.roleCodes()::contains),
                403,
                "GRANT_EXCEEDS_ACTOR");
    }

    public boolean allows(ApiOperation operation, Effective effective) {
        if (!operation.enabled()) return false;
        if (operation.policy() == ApiOperation.Policy.PUBLIC) return true;
        if (operation.policy() == ApiOperation.Policy.AUTHENTICATED) return true;
        if (effective.superAdministrator()) return true;
        return (operation.permissionCode() == null
                        || effective.has(operation.permissionCode(), Permission.Kind.API))
                && (operation.requiredRole() == null
                        || effective.roleCodes().contains(operation.requiredRole()))
                && (operation.permissionCode() != null || operation.requiredRole() != null);
    }
}
