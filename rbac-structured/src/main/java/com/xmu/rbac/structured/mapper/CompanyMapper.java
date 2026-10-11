package com.xmu.rbac.structured.mapper;

import com.xmu.rbac.structured.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanyMapper extends JpaRepository<Company, Long> {

    Optional<Company> findByCompanyCode(String companyCode);
}
