package com.xmu.rbac.structured.dto;

import java.util.List;

/** 资源、操作、权限、菜单、组织。 */
public final class CatalogDtos {

    private CatalogDtos() {
    }

    public record ModuleView(Long id, String moduleCode, String moduleName) {
    }

    public record SystemView(Long id, String systemCode, String systemName, List<ModuleView> modules) {
    }

    public record ResourceView(Long id, String resourceCode, String resourceName, String resourceType,
                               String description, String moduleName, String systemName) {
    }

    public record ActionView(Long id, String actionCode, String actionName) {
    }

    public record PermissionView(Long id, String permissionCode, String resourceName, String resourceType,
                                 String actionName, String systemName) {
    }

    /** 新建资源，同时为 actionCodes 中的每个操作建立权限。 */
    public record CreateResource(Long moduleId, String resourceCode, String resourceName, String resourceType,
                                 String description, List<String> actionCodes) {
    }

    public record CreatePermission(Long resourceId, String actionCode) {
    }

    public record MenuNode(Long id, Long parentId, String menuName, String routePath, String componentKey,
                           String icon, int sortOrder, Long resourceId, String resourceCode,
                           List<MenuNode> children) {
    }

    public record SaveMenu(Long parentId, Long resourceId, String menuName, String routePath,
                           String componentKey, String icon, Integer sortOrder) {
    }

    public record CompanyView(Long id, String companyCode, String companyName, Long rootDepartmentId) {
    }

    public record DeptNode(Long id, Long parentId, String deptName, int level, String managerName,
                           Long managerUserId, List<DeptNode> children) {
    }
}
