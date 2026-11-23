package com.xmu.rbac.structured.dto;
import cn.edu.xmu.clonefactory.CopyTo;
import com.xmu.rbac.structured.entity.Department;
import com.xmu.rbac.structured.entity.Permission;
import com.xmu.rbac.structured.entity.Role;
import com.xmu.rbac.structured.entity.SoftwareSystem;
import com.xmu.rbac.structured.entity.User;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
/** HTTP 输入白名单；身份、操作者、内部版本不由客户端提供。 */
public final class Inputs {
    private Inputs() {
    }
    public record Login(@NotBlank @Size(max = 128) String username,
        @NotBlank @Size(max = 256) String password,
        @NotNull @Size(min = 1, max = 1) List<@NotBlank String> systems) {}
    public record Password(
        @NotBlank String oldPassword, @NotBlank @Size(min = 8, max = 256) String newPassword) {}
    /** 系统创建输入仅提供业务编码和显示名称。 */
    @CopyTo(SoftwareSystem.class)
    public record SystemCreate(
        @NotBlank
        @Size(max = 64)
        String code,
        @NotBlank
        @Size(max = 128)
        String name) {
    }
    public record Name(@NotBlank @Size(max = 128) String name) {}
    public record
        State(@NotBlank @Pattern(regexp = "ENABLED|DISABLED|ACTIVE|DELETED") String status) {}
    public record EnabledState(@NotBlank @Pattern(regexp = "ENABLED|DISABLED") String status) {}
    /** 部门父节点经服务解析后赋值。 */
    @CopyTo(Department.class)
    public record DepartmentCreate(
        @CopyTo.Exclude(Department.class)
        @Positive
        Long parentId,
        @NotBlank
        @Size(max = 64)
        String code,
        @NotBlank
        @Size(max = 128)
        String name) {
    }
    /** 部门由服务校验，初始密码只用于生成哈希。 */
    @CopyTo(User.class)
    public record UserCreate(
        @NotBlank
        @Size(max = 128)
        String username,
        @NotBlank
        @Size(max = 128)
        String name,
        @Size(max = 64)
        String no,
        @Size(max = 32)
        String phone,
        @CopyTo.Exclude(User.class)
        @NotNull
        @Positive
        Long departmentId,
        @CopyTo.Exclude(User.class)
        @NotBlank
        @Size(min = 8, max = 256)
        String password) {
    }
    public static class UserPatch {
        private final java.util.Set<String> present = new java.util.HashSet<>();
        public boolean supplied(String field) {
            return present.contains(field);
        }
        @Size(min = 1, max = 128) private String name;
        public String name() {
            return name;
        }
        @JsonSetter(nulls = Nulls.FAIL)
        public void setName(String value) {
            present.add("name");
            name = value;
        }
        @Size(max = 64) private String no;
        public String no() {
            return no;
        }
        public void setNo(String value) {
            present.add("no");
            no = value;
        }
        @Size(max = 32) private String phone;
        public String phone() {
            return phone;
        }
        public void setPhone(String value) {
            present.add("phone");
            phone = value;
        }
        @PositiveOrZero private Long version;
        public Long version() {
            return version;
        }
        @JsonSetter(nulls = Nulls.FAIL)
        public void setVersion(Long value) {
            present.add("version");
            version = value;
        }
    }

    public record Move(@NotNull @Positive Long departmentId, @PositiveOrZero Long version) {}
    /** 角色所属系统和默认状态由服务解析。 */
    @CopyTo(Role.class)
    public record RoleCreate(
        @NotBlank
        @Size(max = 64)
        String systemCode,
        @NotBlank
        @Size(max = 64)
        String code,
        @NotBlank
        @Size(max = 128)
        String name,
        @Size(max = 500)
        String description,
        @Pattern(regexp = "student|teacher|admin")
        String identityRole) {
    }
    public static class RolePatch {
        private final java.util.Set<String> present = new java.util.HashSet<>();
        public boolean supplied(String field) {
            return present.contains(field);
        }
        @Size(min = 1, max = 128) @Pattern(regexp = "(?s).*\\S.*") private String name;
        public String name() {
            return name;
        }
        @JsonSetter(nulls = Nulls.FAIL)
        public void setName(String value) {
            present.add("name");
            name = value;
        }
        @Size(max = 500) private String description;
        public String description() {
            return description;
        }
        public void setDescription(String value) {
            present.add("description");
            description = value;
        }
        @PositiveOrZero private Long version;
        public Long version() {
            return version;
        }
        @JsonSetter(nulls = Nulls.FAIL)
        public void setVersion(Long value) {
            present.add("version");
            version = value;
        }
    }

    public record Roles(@NotNull @Size(max = 1000) List<@NotNull @Positive Long> roleIds) {}
    public record
        Permissions(@NotNull @Size(max = 1000) List<@NotNull @Positive Long> permissionIds) {}
    public record Assignment(@NotNull @Positive Long roleId) {}
    /** 权限所属系统及启用状态由服务确定。 */
    @CopyTo(Permission.class)
    public record PermissionCreate(
        @NotBlank
        @Size(max = 64)
        String systemCode,
        @NotBlank
        @Size(max = 128)
        String code,
        @NotBlank
        @Size(max = 128)
        String name,
        @NotBlank
        @Pattern(regexp = "UI|API")
        String kind) {
    }
    public record
        Ui(@Positive Long parentId, @NotBlank @Pattern(regexp = "MENU|BUTTON") String type,
            @NotBlank @Size(max = 128) String code, @NotBlank @Size(max = 128) String name,
            @Size(max = 255) String route, @Size(max = 64) String component, Integer sortOrder,
            @NotBlank String permissionCode, @Pattern(regexp = "ENABLED|DISABLED") String status) {}
    public record Api(@NotBlank @Size(max = 128) String operationId,
        @NotBlank @Pattern(regexp = "GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS") String method,
        @NotBlank @Size(max = 500) @Pattern(regexp = "^/.*") String pathTemplate,
        String permissionCode, @Size(max = 64) String requiredRole,
        @NotBlank @Pattern(regexp = "PUBLIC|AUTHENTICATED|PROTECTED") String accessPolicy,
        @NotNull Boolean enabled) {}
    public record
        Mapping(@NotNull @Positive Long uiResourceId, @NotNull @Positive Long apiOperationId) {}
    public record Mappings(@NotNull @Size(max = 10000) List<@Valid Mapping> mappings) {}
    public record Check(@NotBlank @Size(max = 64) String systemCode, @Positive Long userId,
        @NotBlank @Pattern(regexp = "UI|API") String kind,
        @Size(min = 1, max = 128) String permission, @Size(min = 1, max = 128) String operationId) {}
    public record Audit(
        @NotBlank @Pattern(
            regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
        String requestId,
        @NotBlank @Pattern(
            regexp = "score_changed|group_changed|milestone_changed|roster_changed") String type,
        @NotBlank @Pattern(
            regexp = "term|course|class|group|milestone|student|teacher|submission|oral_record")
        String objectType,
        @NotBlank @Pattern(regexp = "cpm-(term|course|class|group|milestone|student|teacher|"
                + "submission|oral_record)-[0-9]+") String objectId,
        @NotBlank @Size(max = 500) String target, JsonNode before, JsonNode after) {}
    public static class UiPatch {
        private final java.util.Set<String> present = new java.util.HashSet<>();
        public boolean supplied(String field) {
            return present.contains(field);
        }
        @Positive private Long parentId;
        public Long parentId() {
            return parentId;
        }
        public void setParentId(Long value) {
            present.add("parentId");
            parentId = value;
        }
        @Pattern(regexp = "MENU|BUTTON") private String type;
        public String type() {
            return type;
        }
        @JsonSetter(nulls = Nulls.FAIL)
        public void setType(String value) {
            present.add("type");
            type = value;
        }
        @Size(min = 1, max = 128) private String code;
        public String code() {
            return code;
        }
        @JsonSetter(nulls = Nulls.FAIL)
        public void setCode(String value) {
            present.add("code");
            code = value;
        }
        @Size(min = 1, max = 128) private String name;
        public String name() {
            return name;
        }
        @JsonSetter(nulls = Nulls.FAIL)
        public void setName(String value) {
            present.add("name");
            name = value;
        }
        @Size(max = 255) private String route;
        public String route() {
            return route;
        }
        public void setRoute(String value) {
            present.add("route");
            route = value;
        }
        @Size(max = 64) private String component;
        public String component() {
            return component;
        }
        public void setComponent(String value) {
            present.add("component");
            component = value;
        }
        private Integer sortOrder;
        public Integer sortOrder() {
            return sortOrder;
        }
        @JsonSetter(nulls = Nulls.FAIL)
        public void setSortOrder(Integer value) {
            present.add("sortOrder");
            sortOrder = value;
        }
        @Size(min = 1, max = 128) private String permissionCode;
        public String permissionCode() {
            return permissionCode;
        }
        @JsonSetter(nulls = Nulls.FAIL)
        public void setPermissionCode(String value) {
            present.add("permissionCode");
            permissionCode = value;
        }
        @Pattern(regexp = "ENABLED|DISABLED") private String status;
        public String status() {
            return status;
        }
        @JsonSetter(nulls = Nulls.FAIL)
        public void setStatus(String value) {
            present.add("status");
            status = value;
        }
    }
    public static class ApiPatch {
        private final java.util.Set<String> present = new java.util.HashSet<>();
        public boolean supplied(String field) {
            return present.contains(field);
        }
        @Size(min = 1, max = 128) private String operationId;
        public String operationId() {
            return operationId;
        }
        @JsonSetter(nulls = Nulls.FAIL)
        public void setOperationId(String value) {
            present.add("operationId");
            operationId = value;
        }
        @Pattern(regexp = "GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS") private String method;
        public String method() {
            return method;
        }
        @JsonSetter(nulls = Nulls.FAIL)
        public void setMethod(String value) {
            present.add("method");
            method = value;
        }
        @Size(min = 1, max = 500) @Pattern(regexp = "^/.*") private String pathTemplate;
        public String pathTemplate() {
            return pathTemplate;
        }
        @JsonSetter(nulls = Nulls.FAIL)
        public void setPathTemplate(String value) {
            present.add("pathTemplate");
            pathTemplate = value;
        }
        @Size(max = 128) private String permissionCode;
        public String permissionCode() {
            return permissionCode;
        }
        public void setPermissionCode(String value) {
            present.add("permissionCode");
            permissionCode = value;
        }
        @Size(max = 64) private String requiredRole;
        public String requiredRole() {
            return requiredRole;
        }
        public void setRequiredRole(String value) {
            present.add("requiredRole");
            requiredRole = value;
        }
        @Pattern(regexp = "PUBLIC|AUTHENTICATED|PROTECTED") private String accessPolicy;
        public String accessPolicy() {
            return accessPolicy;
        }
        @JsonSetter(nulls = Nulls.FAIL)
        public void setAccessPolicy(String value) {
            present.add("accessPolicy");
            accessPolicy = value;
        }
        private Boolean enabled;
        public Boolean enabled() {
            return enabled;
        }
        @JsonSetter(nulls = Nulls.FAIL)
        public void setEnabled(Boolean value) {
            present.add("enabled");
            enabled = value;
        }
    }
}
