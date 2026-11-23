package com.xmu.rbac.ddd.domain;

import com.xmu.rbac.ddd.domain.port.DirectoryRepository;

/** 组织管理范围和部门树不变量，不参与业务对象数据授权。 */
public final class OrganizationPolicy {
    public void requireManaged(String actorPath, DirectoryRepository.Department target) {
        DepartmentEligibility.requireManaged(actorPath, target.path());
    }

    public void requireRoot(String actorPath) {
        DomainException.require(actorPath.equals("/1/"), 403, "ROOT_DEPARTMENT_REQUIRED");
    }

    public void requireDeletable(DirectoryRepository.Department department, boolean occupied) {
        DomainException.require(
                department.parentId() != null && !occupied, 409, "DEPARTMENT_NOT_EMPTY");
    }

    public void requireDisableable(DirectoryRepository.Department department, boolean hasChildren) {
        DomainException.require(
                department.parentId() != null, 409, "ROOT_DEPARTMENT_DISABLE_FORBIDDEN");
        DomainException.require(!hasChildren, 409, "DEPARTMENT_HAS_CHILDREN");
    }

    public void requireAccepting(DirectoryRepository.Department department) {
        DomainException.require("ENABLED".equals(department.status()), 409, "DEPARTMENT_DISABLED");
    }

    public void requireVersion(Long expected, Long actual) {
        DomainException.require(
                expected == null || expected.equals(actual), 409, "VERSION_CONFLICT");
    }
}
