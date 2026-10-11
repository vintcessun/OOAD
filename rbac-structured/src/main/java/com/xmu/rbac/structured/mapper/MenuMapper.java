package com.xmu.rbac.structured.mapper;

import com.xmu.rbac.structured.entity.Menu;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MenuMapper extends JpaRepository<Menu, Long> {

    List<Menu> findAllByOrderBySortOrderAscIdAsc();

    boolean existsByParentId(Long parentId);
}
