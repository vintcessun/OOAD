package com.xmu.rbac.structured.data;

/**
 * 授权判定结果，对应契约中的 AuthzDecision。
 * 「拒绝」也是一次成功的判定：调用方问「他能不能做」，系统答「不能」。
 */
public record AuthzResult(
        boolean allowed,
        String reason,              // 拒绝原因；允许时为 null；超管旁路为 SUPER_ADMIN_BYPASS
        String requiredPermission,
        boolean cached,
        long elapsedMicros
) {
    public static final String MISSING_PERMISSION = "MISSING_PERMISSION";
    public static final String SUBJECT_INVALID = "SUBJECT_INVALID";
    public static final String SUBJECT_DISABLED = "SUBJECT_DISABLED";
    public static final String SUBJECT_DELETED = "SUBJECT_DELETED";
    public static final String SUBJECT_EXPIRED = "SUBJECT_EXPIRED";
    public static final String SUPER_ADMIN_BYPASS = "SUPER_ADMIN_BYPASS";

    public static AuthzResult allow(String permission, boolean cached, long elapsedMicros) {
        return new AuthzResult(true, null, permission, cached, elapsedMicros);
    }

    public static AuthzResult deny(String reason, String permission, long elapsedMicros) {
        return new AuthzResult(false, reason, permission, false, elapsedMicros);
    }

    public static AuthzResult bypass(String permission, long elapsedMicros) {
        return new AuthzResult(true, SUPER_ADMIN_BYPASS, permission, false, elapsedMicros);
    }
}
