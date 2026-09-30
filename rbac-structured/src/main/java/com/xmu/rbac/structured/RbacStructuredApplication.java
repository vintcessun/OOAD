package com.xmu.rbac.structured;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.xmu.rbac.structured.dao")
public class RbacStructuredApplication {

    public static void main(String[] args) {
        SpringApplication.run(RbacStructuredApplication.class, args);
    }
}
