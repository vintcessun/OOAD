package com.xmu.rbac.structured.mapper;

import com.xmu.rbac.structured.entity.Permission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PermissionMapper extends JpaRepository<Permission, Long> {

    Optional<Permission> findByPermissionCode(String permissionCode);

    boolean existsByResourceIdAndActionId(Long resourceId, Long actionId);

    List<Permission> findByResourceId(Long resourceId);

    List<Permission> findByResourceIdIn(Collection<Long> resourceIds);

    /** 权限列表：[permission, resourceName, resourceType, actionName, systemName]。 */
    @Query(value = """
            select p, r.resourceName, r.resourceType, a.actionName, s.systemName
              from Permission p, Resource r, Action a, FunctionModule m, SoftwareSystem s
             where r.id = p.resourceId and a.id = p.actionId and m.id = r.moduleId and s.id = m.systemId
               and (:systemId is null or s.id = :systemId)
               and (:resourceId is null or r.id = :resourceId)
               and (:keyword is null or p.permissionCode like concat('%', :keyword, '%')
                                     or r.resourceName like concat('%', :keyword, '%'))
             order by p.permissionCode
            """,
            countQuery = """
            select count(p) from Permission p, Resource r, FunctionModule m, SoftwareSystem s
             where r.id = p.resourceId and m.id = r.moduleId and s.id = m.systemId
               and (:systemId is null or s.id = :systemId)
               and (:resourceId is null or r.id = :resourceId)
               and (:keyword is null or p.permissionCode like concat('%', :keyword, '%')
                                     or r.resourceName like concat('%', :keyword, '%'))
            """)
    Page<Object[]> search(@Param("systemId") Long systemId,
                          @Param("resourceId") Long resourceId,
                          @Param("keyword") String keyword,
                          Pageable pageable);
}
