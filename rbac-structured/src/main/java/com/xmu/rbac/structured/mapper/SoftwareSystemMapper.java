package com.xmu.rbac.structured.mapper;

import com.xmu.rbac.structured.entity.SoftwareSystem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SoftwareSystemMapper extends JpaRepository<SoftwareSystem, Long> {

    Optional<SoftwareSystem> findBySystemCode(String systemCode);

    List<SoftwareSystem> findAllByOrderBySortOrderAscIdAsc();
}
