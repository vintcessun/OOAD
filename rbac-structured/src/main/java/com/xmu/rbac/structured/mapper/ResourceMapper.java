package com.xmu.rbac.structured.mapper;

import com.xmu.rbac.structured.entity.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ResourceMapper extends JpaRepository<Resource, Long> {

    Optional<Resource> findByResourceCode(String resourceCode);

    boolean existsByResourceCode(String resourceCode);

    List<Resource> findByModuleIdOrderByIdAsc(Long moduleId);

    /** 资源列表：[resource, moduleName, systemName]。 */
    @Query(value = """
            select r, m.moduleName, s.systemName
              from Resource r, FunctionModule m, SoftwareSystem s
             where m.id = r.moduleId and s.id = m.systemId
               and (:systemId is null or s.id = :systemId)
               and (:type is null or r.resourceType = :type)
               and (:keyword is null or r.resourceName like concat('%', :keyword, '%')
                                     or r.resourceCode like concat('%', :keyword, '%'))
             order by s.sortOrder, m.sortOrder, r.sortOrder, r.id
            """,
            countQuery = """
            select count(r) from Resource r, FunctionModule m, SoftwareSystem s
             where m.id = r.moduleId and s.id = m.systemId
               and (:systemId is null or s.id = :systemId)
               and (:type is null or r.resourceType = :type)
               and (:keyword is null or r.resourceName like concat('%', :keyword, '%')
                                     or r.resourceCode like concat('%', :keyword, '%'))
            """)
    Page<Object[]> search(@Param("systemId") Long systemId,
                          @Param("type") String type,
                          @Param("keyword") String keyword,
                          Pageable pageable);
}
