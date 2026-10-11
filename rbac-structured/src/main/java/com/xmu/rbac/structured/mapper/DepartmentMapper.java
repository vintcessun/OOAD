package com.xmu.rbac.structured.mapper;

import com.xmu.rbac.structured.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DepartmentMapper extends JpaRepository<Department, Long> {

    List<Department> findByCompanyIdOrderByLevelAscSortOrderAscIdAsc(Long companyId);

    Optional<Department> findByCompanyIdAndParentIdIsNull(Long companyId);

    Optional<Department> findByCompanyIdAndParentIdAndDeptName(Long companyId, Long parentId, String deptName);

    @Query("select d.path from Department d where d.id = :id")
    Optional<String> findPathById(@Param("id") Long id);

    @Query("select d from Department d where d.path like concat(:path, '%') order by d.level, d.sortOrder, d.id")
    List<Department> findSubtree(@Param("path") String path);
}
