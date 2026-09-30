package com.xmu.rbac.structured.web;

import java.time.OffsetDateTime;

/** 统一响应。code = 0 成功，其余取契约 x-error-codes。 */
public record ApiResponse<T>(int code, String message, T data, OffsetDateTime timestamp) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "success", data, OffsetDateTime.now());
    }

    public static <T> ApiResponse<T> fail(int code, String message) {
        return new ApiResponse<>(code, message, null, OffsetDateTime.now());
    }

    public static <T> ApiResponse<T> fail(int code, String message, T data) {
        return new ApiResponse<>(code, message, data, OffsetDateTime.now());
    }
}
