package com.xmu.rbac.structured.mapper;

import com.xmu.rbac.structured.entity.FunctionModule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FunctionModuleMapper extends JpaRepository<FunctionModule, Long> {

    Optional<FunctionModule> findBySystemIdAndModuleCode(Long systemId, String moduleCode);

    List<FunctionModule> findBySystemIdOrderBySortOrderAsc(Long systemId);

    List<FunctionModule> findAllByOrderBySystemIdAscSortOrderAsc();
}
