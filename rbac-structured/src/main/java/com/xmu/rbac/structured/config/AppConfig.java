package com.xmu.rbac.structured.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmu.rbac.structured.controller.ContractRoutes;
import com.xmu.rbac.structured.controller.PermissionInterceptor;
import com.xmu.rbac.structured.service.AuditService;
import com.xmu.rbac.structured.service.AuthenticationService;
import com.xmu.rbac.structured.service.AuthorizationService;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;

/** 装配：时钟、口令编码器、JWT 密钥、契约路由表与拦截器。 */
@Configuration
public class AppConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public SecretKey jwtKey(@Value("${rbac.jwt.secret}") String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("rbac.jwt.secret 至少 32 字节（环境变量 RBAC_JWT_SECRET）");
        }
        return Keys.hmacShaKeyFor(bytes);
    }

    @Bean
    public ContractRoutes contractRoutes() throws IOException {
        return ContractRoutes.load(new ClassPathResource("openapi/rbac-api.yaml").getInputStream());
    }

    @Bean
    public PermissionInterceptor permissionInterceptor(ContractRoutes routes, AuthenticationService authentication,
                                                       AuthorizationService authorization, AuditService audit,
                                                       ObjectMapper json) {
        return new PermissionInterceptor(routes, authentication, authorization, audit, json);
    }
}
