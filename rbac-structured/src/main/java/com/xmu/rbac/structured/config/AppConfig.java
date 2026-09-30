package com.xmu.rbac.structured.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmu.rbac.structured.dao.AssignmentDao;
import com.xmu.rbac.structured.dao.UserDao;
import com.xmu.rbac.structured.func.CacheHandle;
import com.xmu.rbac.structured.web.ContractRoutes;
import com.xmu.rbac.structured.web.PermissionInterceptor;
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
import java.time.Duration;

/** 装配：把功能层需要的依赖造出来。功能层本身不认识 Spring。 */
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
    public CacheHandle cacheHandle(@Value("${rbac.cache.l1-max-size:100000}") long maxSize,
                                   @Value("${rbac.cache.l1-ttl:PT10M}") Duration ttl) {
        return new CaffeineCacheHandle(maxSize, ttl);
    }

    @Bean
    public ContractRoutes contractRoutes() throws IOException {
        return ContractRoutes.load(new ClassPathResource("openapi/rbac-api.yaml").getInputStream());
    }

    @Bean
    public PermissionInterceptor permissionInterceptor(ContractRoutes routes, SecretKey jwtKey, UserDao userDao,
                                                       AssignmentDao assignmentDao, CacheHandle cache,
                                                       ObjectMapper json, Clock clock) {
        return new PermissionInterceptor(routes, jwtKey, userDao, assignmentDao, cache, json, clock);
    }
}
