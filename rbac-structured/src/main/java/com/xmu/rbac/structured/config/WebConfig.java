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
        RequestMappingHandlerMapping mapping = event.getApplicationContext()
                .getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class);
        List<String> implemented = new ArrayList<>();
        mapping.getHandlerMethods().forEach((info, method) -> {
            if (info.getPathPatternsCondition() == null) {
                return;
            }
            for (String path : info.getPathPatternsCondition().getPatternValues()) {
                if (path.equals("/error")) {
                    continue;
                }
                for (RequestMethod m : info.getMethodsCondition().getMethods()) {
                    implemented.add(ContractRoutes.key(m.name(), path));
                }
            }
        });
        List<String> missing = routes.undeclared(implemented);
        if (!missing.isEmpty()) {
            throw new IllegalStateException("以下路由未在 rbac-api.yaml 中声明 x-required-permission：" + missing);
        }
        List<String> unimplemented = routes.unimplemented(implemented);
        if (!unimplemented.isEmpty()) {
            throw new IllegalStateException("rbac-api.yaml 中的以下接口没有实现：" + unimplemented);
        }
    }
}
