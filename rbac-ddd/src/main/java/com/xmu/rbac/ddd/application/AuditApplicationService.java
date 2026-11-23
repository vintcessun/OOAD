package com.xmu.rbac.ddd.application;

import com.fasterxml.jackson.databind.*;
import com.xmu.rbac.ddd.domain.*;
import com.xmu.rbac.ddd.domain.port.*;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;

/** 同步审计：内部调用加入原事务，外部插入先提交再响应，拒绝独立事务。 */
@Service
public class AuditApplicationService {
    private final AuditRepository repository;
    private final AuthorizationRepository grants;
    private final ObjectMapper mapper;
    private final TransactionTemplate fresh;
    private static final Set<String> SAFE =
            Set.of(
                    "status",
                    "score",
                    "oldScore",
                    "newScore",
                    "name",
                    "code",
                    "roleCode",
                    "permissionCode",
                    "groupId",
                    "studentId",
                    "teacherId",
                    "milestoneId",
                    "courseId",
                    "classId",
                    "termId",
                    "submissionId",
                    "oralRecordId",
                    "memberIds",
                    "leaderId",
                    "reason",
                    "value",
                    "from",
                    "to",
                    "description",
                    "title",
                    "count",
                    "state",
                    "id",
                    "no",
                    "grade",
                    "comment",
                    "members",
                    "removed",
                    "added");

    public AuditApplicationService(
            AuditRepository repository,
            AuthorizationRepository grants,
            ObjectMapper mapper,
            PlatformTransactionManager manager) {
        this.repository = repository;
        this.grants = grants;
        this.mapper = mapper;
        fresh = new TransactionTemplate(manager);
        fresh.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public AuditFact local(
            AuthorizationRepository.Identity actor,
            String system,
            String type,
            String target,
            String result,
            String reason) {
        List<String> roles = List.of();
        if (actor != null && actor.status() == User.Status.ACTIVE) {
            try {
                if (grants.systemEnabled(system)) {
                    AuthorizationPolicy.Effective effective =
                            new AuthorizationPolicy()
                                    .calculate(
                                            system,
                                            grants.assignments(actor.id(), system),
                                            grants.hierarchy(system),
                                            grants.permissions(system));
                    roles = new ArrayList<>(new TreeSet<>(effective.courseRoleCodes()));
                }
            } catch (DomainException error) {
                if (error.status() != 404) throw error;
            }
        }

        AuditFact fact =
                new AuditFact(
                        null,
                        UUID.randomUUID().toString(),
                        system,
                        "INTERNAL",
                        actor == null ? null : actor.id(),
                        actor == null ? null : actor.subject(),
                        actor == null ? null : actor.name(),
                        roles,
                        type,
                        null,
                        null,
                        target,
                        null,
                        null,
                        result,
                        reason,
                        UUID.randomUUID().toString(),
                        hash(type + ":" + target),
                        Instant.now());
        return repository.append(fact);
    }

    public void denied(
            AuthorizationRepository.Identity actor, String system, String target, String reason) {
        fresh.execute(
                status -> {
                    local(actor, system, "AUTHZ_DENIED", target, "DENIED", reason);
                    return null;
                });
    }

    private JsonNode canonical(JsonNode node) {
        if (node == null || node.isNull()) return mapper.nullNode();
        DomainException.require(node.toString().length() <= 16384, 400, "AUDIT_JSON_TOO_LARGE");
        if (node.isObject()) {
            com.fasterxml.jackson.databind.node.ObjectNode result = mapper.createObjectNode();
            TreeSet<String> fields = new TreeSet<>();
            node.fieldNames().forEachRemaining(fields::add);
            for (String field : fields) {
                DomainException.require(SAFE.contains(field), 400, "AUDIT_UNSAFE_FIELD");
                result.set(field, canonical(node.get(field)));
            }
            return result;
        }
        if (node.isArray()) {
            DomainException.require(node.size() <= 100, 400, "AUDIT_JSON_TOO_LARGE");
            com.fasterxml.jackson.databind.node.ArrayNode result = mapper.createArrayNode();
            node.forEach(n -> result.add(canonical(n)));
            return result;
        }
        DomainException.require(
                !node.isTextual() || node.asText().length() <= 1000, 400, "AUDIT_JSON_TOO_LARGE");
        return node;
    }

    private static String hash(String source) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public AuditFact external(
            AuthorizationRepository.Identity actor,
            String system,
            List<String> roles,
            String requestId,
            String type,
            String objectType,
            String objectId,
            String target,
            JsonNode before,
            JsonNode after) {
        DomainException.require(
                Set.of("score_changed", "group_changed", "milestone_changed", "roster_changed")
                        .contains(type),
                400,
                "INVALID_AUDIT_TYPE");
        DomainException.require(
                Set.of(
                                        "term",
                                        "course",
                                        "class",
                                        "group",
                                        "milestone",
                                        "student",
                                        "teacher",
                                        "submission",
                                        "oral_record")
                                .contains(objectType)
                        && objectId.matches("^cpm-" + objectType + "-[0-9]+$"),
                400,
                "INVALID_AUDIT_OBJECT");
        String beforeJson = before == null ? null : canonical(before).toString();
        String afterJson = after == null ? null : canonical(after).toString();
        String content =
                system
                        + "\n"
                        + actor.subject()
                        + "\n"
                        + type
                        + "\n"
                        + objectType
                        + "\n"
                        + objectId
                        + "\n"
                        + target
                        + "\n"
                        + beforeJson
                        + "\n"
                        + afterJson;
        AuditFact fact =
                new AuditFact(
                        null,
                        requestId,
                        system,
                        "COURSE",
                        actor.id(),
                        actor.subject(),
                        actor.name(),
                        roles,
                        type,
                        objectType,
                        objectId,
                        target,
                        beforeJson,
                        afterJson,
                        "SUCCESS",
                        null,
                        UUID.randomUUID().toString(),
                        hash(content),
                        Instant.now());
        try {
            return fresh.execute(
                    status -> {
                        AuditFact existing = repository.find(requestId);
                        if (existing != null) {
                            existing.requireReplay(fact);
                            return existing;
                        }
                        return repository.append(fact);
                    });
        } catch (DataIntegrityViolationException conflict) {
            return fresh.execute(
                    status -> {
                        AuditFact existing = repository.find(requestId);
                        if (existing == null) throw conflict;
                        existing.requireReplay(fact);
                        return existing;
                    });
        }
    }

    public AuthorizationRepository.Page<AuditFact> query(
            String system,
            boolean external,
            String actor,
            Long actorId,
            String target,
            Instant from,
            Instant to,
            int page,
            int size) {
        DomainException.require(
                page > 0
                        && size > 0
                        && size <= 100
                        && (from == null || to == null || !from.isAfter(to)),
                400,
                "INVALID_PAGE_OR_TIME");
        return repository.query(system, external, actor, actorId, target, from, to, page, size);
    }

    public boolean actorDeleted(AuditFact fact) {
        if (fact.actorSubject() == null) return false;
        AuthorizationRepository.Identity actor = grants.identity(fact.actorSubject());
        return actor == null || actor.status() == User.Status.DELETED;
    }
}
