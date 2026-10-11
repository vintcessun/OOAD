package com.xmu.rbac.structured.dto;

/**
 * 当前请求的操作者：由拦截器在认证后生成，Controller 显式传给 Service。
 * 「谁、在哪里」随数据流一路传到审计，不藏在线程变量里。
 */
public record Operator(Long userId, String username, String clientIp) {
}
