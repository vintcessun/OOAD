package com.xmu.rbac.structured.service;

/**
 * 业务处理过程的异常出口：HTTP 状态 + 错误码 + 说明。
 * 状态码口径（docs/04-api.md §1.3）：401 未认证；403 无操作权限或超出管理范围；404 目标不存在；
 * 409 重复或状态冲突；400 参数不合法。鉴权接口判定为「无权限」时不抛异常，返回 allowed=false。
 */
public class BizException extends RuntimeException {

    public static final int VALIDATION = 40001;
    public static final int UNAUTHENTICATED = 40101;
    public static final int CREDENTIAL_INVALID = 40102;
    public static final int ACCOUNT_LOCKED = 40103;
    public static final int ACCOUNT_DISABLED = 40104;
    public static final int FORBIDDEN = 40301;
    public static final int OUT_OF_MANAGE_SCOPE = 40302;
    public static final int PRIVILEGE_ESCALATION = 40303;
    public static final int PASSWORD_CHANGE_REQUIRED = 40304;
    public static final int NOT_FOUND = 40401;
    public static final int DUPLICATE = 40901;
    public static final int STATE_CONFLICT = 40902;

    private final int httpStatus;
    private final int code;

    public BizException(int httpStatus, int code, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.code = code;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public int code() {
        return code;
    }

    public static BizException badRequest(String message) {
        return new BizException(400, VALIDATION, message);
    }

    public static BizException notFound(String what) {
        return new BizException(404, NOT_FOUND, what + "不存在");
    }

    public static BizException forbidden(int code, String message) {
        return new BizException(403, code, message);
    }

    public static BizException conflict(int code, String message) {
        return new BizException(409, code, message);
    }
}
