package com.xmu.rbac.structured.config;

import com.xmu.rbac.structured.controller.ContractRoutes;
import com.xmu.rbac.structured.controller.PermissionInterceptor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.List;

/**
 * 拦截器挂在全部业务路由上，只放行 /error（Spring 内部转发错误页用）。
 * 健康检查 /actuator/health 由 actuator 自己的映射处理，不经过本拦截器。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final PermissionInterceptor interceptor;
    private final ContractRoutes routes;

    public WebConfig(PermissionInterceptor interceptor, ContractRoutes routes) {
        this.interceptor = interceptor;
        this.routes = routes;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/**").excludePathPatterns("/error");
    }

    /**
     * 启动自检，两个方向都查：实现了但契约没声明权限的路由，契约里有但没实现的接口。
     * 任何一边不一致都不允许启动。
     */
    @EventListener
    public void verifyEveryRouteDeclared(ApplicationReadyEvent event) {
        // 1. 应用启动完成后，获取 Spring MVC 的路由映射器
        RequestMappingHandlerMapping mapping = event.getApplicationContext()
                .getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class);
        List<String> implemented = new ArrayList<>();

        // 2. 遍历所有已实现的 Controller 方法，提取出 "方法名:路径" (如 GET:/users/{id})
        mapping.getHandlerMethods().forEach((info, method) -> {
            // 忽略没有路径条件的映射（如某些特殊的全局映射）
            if (info.getPathPatternsCondition() == null) {
                return;
            }
            // 遍历该映射匹配的所有路径模板（因为一个方法可能对应多个路径）
            for (String path : info.getPathPatternsCondition().getPatternValues()) {
                if (path.equals("/error")) {
                    // 排除 Spring Boot 内置的 /error 默认路由
                    continue;
                }
                // 遍历该方法支持的 HTTP 请求方法（GET/POST/PUT/DELETE等）
                for (RequestMethod m : info.getMethodsCondition().getMethods()) {
                    // 拼装成统一格式的 Key，例如 "GET:/api/v1/users/{id}"，存入已实现列表
                    implemented.add(ContractRoutes.key(m.name(), path));
                }
            }
        });

        // 3. 一致性校验（防止代码与契约脱节）：

        // [双向校验 A] 查漏：是否有代码里写了接口，但在 yaml 契约里没有配置权限声明
        List<String> missing = routes.undeclared(implemented);
        if (!missing.isEmpty()) {
            throw new IllegalStateException("以下路由未在 rbac-api.yaml 中声明 x-required-permission：" + missing);
        }

        // [双向校验 B] 查空：是否有 yaml 契约里写了接口，但代码里根本没有实现
        List<String> unimplemented = routes.unimplemented(implemented);
        if (!unimplemented.isEmpty()) {
            throw new IllegalStateException("rbac-api.yaml 中的以下接口没有实现：" + unimplemented);
        }
    }
}
