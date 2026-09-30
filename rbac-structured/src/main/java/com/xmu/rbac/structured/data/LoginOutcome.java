package com.xmu.rbac.structured.data;

/** 登录校验的结果。errorCode = 0 表示成功，其余取契约 x-error-codes。 */
public record LoginOutcome(int errorCode, UserRecord user) {

    public static final int OK = 0;
    public static final int CREDENTIAL_INVALID = 20002;
    public static final int USER_DISABLED = 20003;
    public static final int USER_LOCKED = 20004;
    public static final int PASSWORD_POLICY_VIOLATION = 28009;

    public boolean success() {
        return errorCode == OK;
    }

    public static LoginOutcome ok(UserRecord user) {
        return new LoginOutcome(OK, user);
    }

    public static LoginOutcome fail(int errorCode) {
        return new LoginOutcome(errorCode, null);
    }
}
