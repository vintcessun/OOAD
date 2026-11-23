package com.xmu.rbac.structured.service;
import static com.xmu.rbac.structured.service.Views.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.xmu.rbac.structured.dto.*;
import com.xmu.rbac.structured.entity.*;
import com.xmu.rbac.structured.mapper.Store;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
/** 同步审计：本地参与调用事务，外部独立提交后返回，唯一冲突在回滚后重读。 */
@Service
public class AuditService {
    private final Store db;
    private final AuthorizationService authz;
    private final ObjectMapper json;
    private final TransactionTemplate isolated;
    private final byte[] keyDigest;
    private static final Set<String> SAFE = Set.of("id", "code", "name", "status", "score",
        "oldScore", "newScore", "remark", "reason", "roleCode", "permissionCode", "operationId",
        "departmentId", "userId", "roleId", "systemCode", "title", "description", "members",
        "memberIds", "studentId", "teacherId", "groupId", "classId", "courseId", "termId",
        "milestoneId", "submissionId", "oralRecordId", "value", "before", "after", "type", "level",
        "result", "count", "enabled", "sortOrder");
    public AuditService(Store db, AuthorizationService authz, ObjectMapper json,
        PlatformTransactionManager manager, @Value("${rbac.audit-service-key:}") String key) {
        this.db = db;
        this.authz = authz;
        this.json = json;
        this.keyDigest = hash(key);
        isolated = new TransactionTemplate(manager);
        isolated.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    private byte[] hash(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(
                value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
    /** 恒定时间校验可信服务通道凭据。 */
    public void trusted(String supplied) {
        byte[] actual = hash(supplied == null ? "" : supplied);
        if (!MessageDigest.isEqual(keyDigest, actual) || MessageDigest.isEqual(keyDigest, hash("")))
            throw BizException.forbidden("AUDIT_SERVICE_REQUIRED");
    }
    /** 执行json操作，保持服务到持久化层的单向依赖。 */
    public String json(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception e) {
            throw BizException.bad("INVALID_JSON");
        }
    }
    private JsonNode safe(JsonNode node, int depth) {
        if (node == null || node.isNull())
            return NullNode.instance;
        if (depth > 8)
            throw BizException.bad("AUDIT_JSON_TOO_DEEP");
        if (node.isObject()) {
            ObjectNode out = json.createObjectNode();
            List<String> names = new ArrayList<>();
            node.fieldNames().forEachRemaining(names::add);
            Collections.sort(names);
            for (String name : names) {
                if (!SAFE.contains(name))
                    throw BizException.bad("AUDIT_UNSAFE_FIELD");
                out.set(name, safe(node.get(name), depth + 1));
            }
            return out;
        }
        if (node.isArray()) {
            if (node.size() > 100)
                throw BizException.bad("AUDIT_JSON_TOO_LARGE");
            ArrayNode out = json.createArrayNode();
            for (JsonNode child : node) out.add(safe(child, depth + 1));
            return out;
        }
        if (node.isTextual() && node.textValue().length() > 1000)
            throw BizException.bad("AUDIT_JSON_TOO_LARGE");
        return node;
    }
    private AuditLog record(Actor a, String requestId, String type, String target, String origin) {
        AuditLog log = new AuditLog();
        log.requestId = requestId;
        log.sourceSystemCode = a == null ? "rbac-center" : a.systemCode();
        log.origin = origin;
        log.actorId = a == null ? null : a.userId();
        log.actorSubject = a == null ? null : a.subject();
        User user = a == null ? null : db.get(User.class, a.userId());
        log.actorName = user == null ? "未知" : user.name;
        log.actorRolesJson = json(a == null
                ? List.of()
                : authz.courseRoleCodes(
                      authz.calculate(authz.direct(a.userId(), authz.system(a.systemCode()).id),
                          authz.system(a.systemCode()).id)));
        log.eventType = type;
        log.target = target;
        log.result = "SUCCESS";
        log.traceId = Optional.ofNullable(org.slf4j.MDC.get("traceId"))
                          .orElseGet(() -> UUID.randomUUID().toString());
        log.payloadHash = "";
        log.occurredAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        return log;
    }
    /** 调用方已持授权锁；业务/版本/SUCCESS审计共享事务。 */
    public void success(Actor a, String type, String target) {
        AuditLog log = record(a, UUID.randomUUID().toString(), type, target, "INTERNAL");
        db.save(log);
    }
    /** 拒绝事实不能随主业务事务回滚。 */
    public void denied(Actor a, String reason) {
        isolated.executeWithoutResult(status -> {
            AuditLog log =
                record(a, UUID.randomUUID().toString(), "AUTHZ_DENIED", "授权请求", "INTERNAL");
            log.result = "DENIED";
            log.reason = reason;
            db.save(log);
            db.flush();
        });
    }
    /** 独立提交外部审计，唯一冲突回滚后重新读取幂等记录。 */
    public Map<String, Object> external(Actor a, Inputs.Audit input, String key) {
        trusted(key);
        if (!authz.permission(a.userId(), a.systemCode(), "API", "audit:write"))
            throw BizException.forbidden("FORBIDDEN");
        if (!input.objectId().startsWith("cpm-" + input.objectType() + "-"))
            throw BizException.bad("AUDIT_OBJECT_MISMATCH");
        if ((input.before() != null && !input.before().isObject())
            || (input.after() != null && !input.after().isObject()))
            throw BizException.bad("AUDIT_JSON_OBJECT_REQUIRED");
        JsonNode before = safe(input.before(), 0), after = safe(input.after(), 0);
        String canonical = json(map("source", a.systemCode(), "subject", a.subject(), "type",
            input.type(), "objectType", input.objectType(), "objectId", input.objectId(), "target",
            input.target(), "before", before, "after", after));
        if (canonical.getBytes(StandardCharsets.UTF_8).length > 16000)
            throw BizException.bad("AUDIT_JSON_TOO_LARGE");
        String payload = HexFormat.of().formatHex(hash(canonical));
        String requestId = UUID.fromString(input.requestId()).toString();
        try {
            return isolated.execute(status -> {
                AuditLog existing = db.one(AuditLog.class, "e.requestId=?1", requestId);
                if (existing != null)
                    return same(existing, payload);
                AuditLog log = record(a, requestId, input.type(), input.target(), "COURSE");
                log.objectType = input.objectType();
                log.objectId = input.objectId();
                log.beforeJson = before.isNull() ? null : json(before);
                log.afterJson = after.isNull() ? null : json(after);
                log.payloadHash = payload;
                db.save(log);
                db.flush();
                return view(log);
            });
        } catch (DataIntegrityViolationException duplicate) {
            return isolated.execute(status -> {
                AuditLog log = db.one(AuditLog.class, "e.requestId=?1", requestId);
                if (log == null)
                    throw duplicate;
                return same(log, payload);
            });
        }
    }
    private Map<String, Object> same(AuditLog log, String payload) {
        if (!MessageDigest.isEqual(log.payloadHash.getBytes(StandardCharsets.UTF_8),
                payload.getBytes(StandardCharsets.UTF_8)))
            throw BizException.bad("AUDIT_DUPLICATE_CONFLICT");
        return view(log);
    }
    private Object node(String text) {
        if (text == null)
            return null;
        try {
            return json.readTree(text);
        } catch (Exception e) {
            throw new IllegalStateException("存储JSON无效");
        }
    }
    /** 执行view操作，保持服务到持久化层的单向依赖。 */
    public Map<String, Object> view(AuditLog log) {
        User actor = log.actorId == null ? null : db.get(User.class, log.actorId);
        return map("id", log.id, "at", log.occurredAt, "actor",
            map("id", log.actorId, "name", log.actorName, "roles", node(log.actorRolesJson),
                "deleted", actor == null || "DELETED".equals(actor.status)),
            "type", log.eventType, "target", log.target, "before", node(log.beforeJson), "after",
            node(log.afterJson), "objectType", log.objectType, "objectId", log.objectId);
    }
    /** 校验查询资格并在授权过滤后执行数据库分页。 */
    public Map<String, Object> query(Actor a, boolean external, String key, String system,
        String actor, Long actorId, String target, Instant from, Instant to, int page, int size) {
        if (external) {
            trusted(key);
            if (!authz.permission(a.userId(), a.systemCode(), "API", "audit:read"))
                throw BizException.forbidden("FORBIDDEN");
            system = a.systemCode();
        } else
            authz.center(a, "rbac:audit:view");
        if (page < 1 || size < 1 || size > 100 || from != null && to != null && from.isAfter(to))
            throw BizException.bad("INVALID_QUERY");
        List<Object> args = new ArrayList<>();
        String clause = "1=1";
        if (system != null) {
            args.add(system);
            clause += " and e.sourceSystemCode=?" + args.size();
        }
        if (external)
            clause += " and e.origin='COURSE' and e.eventType in "
                + "('score_changed','group_changed','milestone_changed','roster_changed')";
        if (actor != null) {
            args.add("%" + actor + "%");
            clause += " and e.actorName like ?" + args.size();
        }
        if (actorId != null) {
            args.add(actorId);
            clause += " and e.actorId=?" + args.size();
        }
        if (target != null) {
            args.add("%" + target + "%");
            clause += " and e.target like ?" + args.size();
        }
        if (from != null) {
            args.add(from);
            clause += " and e.occurredAt>=?" + args.size();
        }
        if (to != null) {
            args.add(to);
            clause += " and e.occurredAt<=?" + args.size();
        }
        return db.page(
            AuditLog.class, clause, args, page, size, l -> view(l), "e.occurredAt desc,e.id desc");
    }
}
