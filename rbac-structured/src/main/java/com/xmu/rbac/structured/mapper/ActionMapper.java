package com.xmu.rbac.structured.mapper;

import com.xmu.rbac.structured.entity.Action;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ActionMapper extends JpaRepository<Action, Long> {

    Optional<Action> findByActionCode(String actionCode);

    List<Action> findAllByOrderBySortOrderAsc();
}
