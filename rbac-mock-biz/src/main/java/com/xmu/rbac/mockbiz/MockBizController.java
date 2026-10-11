package com.xmu.rbac.mockbiz;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 外部模拟业务系统：从权限清单取两个功能点，验证权限在业务系统里真实生效。
 * <ul>
 *   <li>OA › 公文管理 › 发文拟稿与审核（oa:m01:f02）：拟稿与审批分属不同角色；</li>
 *   <li>人力资源 › 考勤与休假管理 › 考勤管理（hr:m04:f01）：普通员工是「查看/编辑本人」。</li>
 * </ul>
 * 每个接口先把用户的令牌原样带去权限中心 /authz/check（查自己，不需要额外权限），
 * 问「这个人能否对这个部门的数据做这个操作」，允许才执行。数据只放在内存里，重启即清空。
 */
@RestController
public class MockBizController {

    public record Doc(Long id, String title, Long deptId, Long draftedBy, String status) {
    }

    public record Attendance(Long id, Long userId, Long deptId, String date, String status) {
    }

    private final RestClient center;
    private final String docResource;
    private final String attendanceResource;
    private final Map<Long, Doc> docs = new ConcurrentHashMap<>();
    private final Map<Long, Attendance> attendance = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong();

    public MockBizController(@Value("${rbac.center-url}") String centerUrl,
                             @Value("${mock.doc-resource}") String docResource,
                             @Value("${mock.attendance-resource}") String attendanceResource) {
        this.center = RestClient.create(centerUrl);
        this.docResource = docResource;
        this.attendanceResource = attendanceResource;
    }

    // ------------------------------------------------------------------ OA 发文

    @GetMapping("/oa/documents")
    public ResponseEntity<Object> listDocuments(@RequestHeader(value = "Authorization", required = false) String auth,
                                                @RequestParam Long deptId) {
        JsonNode d = check(auth, docResource + ":view", deptId);
        if (!allowed(d)) {
            return denied(d);
        }
        return ResponseEntity.ok(docs.values().stream().filter(x -> x.deptId().equals(deptId)).toList());
    }

    @PostMapping("/oa/documents")
    public ResponseEntity<Object> createDocument(@RequestHeader(value = "Authorization", required = false) String auth,
                                                 @RequestBody Map<String, Object> body) {
        Long deptId = ((Number) body.get("deptId")).longValue();
        JsonNode d = check(auth, docResource + ":create", deptId);
        if (!allowed(d)) {
            return denied(d);
        }
        Doc doc = new Doc(ids.incrementAndGet(), String.valueOf(body.get("title")), deptId,
                d.path("userId").asLong(), "SUBMITTED");
        docs.put(doc.id(), doc);
        return ResponseEntity.ok(doc);
    }

    @PostMapping("/oa/documents/{id}/approve")
    public ResponseEntity<Object> approveDocument(@RequestHeader(value = "Authorization", required = false) String auth,
                                                  @PathVariable Long id) {
        Doc doc = docs.get(id);
        if (doc == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "公文不存在"));
        }
        JsonNode d = check(auth, docResource + ":approve", doc.deptId());
        if (!allowed(d)) {
            return denied(d);
        }
        Doc approved = new Doc(doc.id(), doc.title(), doc.deptId(), doc.draftedBy(), "APPROVED");
        docs.put(id, approved);
        return ResponseEntity.ok(approved);
    }

    // ------------------------------------------------------------------ HR 考勤

    @PostMapping("/hr/attendance")
    public ResponseEntity<Object> createAttendance(@RequestHeader(value = "Authorization", required = false) String auth,
                                                   @RequestBody Map<String, Object> body) {
        Long deptId = ((Number) body.get("deptId")).longValue();
        JsonNode d = check(auth, attendanceResource + ":edit", deptId);
        if (!allowed(d)) {
            return denied(d);
        }
        Long userId = body.get("userId") == null ? d.path("userId").asLong() : ((Number) body.get("userId")).longValue();
        if (d.path("selfOnly").asBoolean() && !userId.equals(d.path("userId").asLong())) {
            return selfOnly();
        }
        Attendance a = new Attendance(ids.incrementAndGet(), userId, deptId, String.valueOf(body.get("date")), "NORMAL");
        attendance.put(a.id(), a);
        return ResponseEntity.ok(a);
    }

    /** 只限本人的权限（selfOnly）：只返回自己的考勤。 */
    @GetMapping("/hr/attendance")
    public ResponseEntity<Object> listAttendance(@RequestHeader(value = "Authorization", required = false) String auth,
                                                 @RequestParam Long deptId) {
        JsonNode d = check(auth, attendanceResource + ":view", deptId);
        if (!allowed(d)) {
            return denied(d);
        }
        boolean selfOnly = d.path("selfOnly").asBoolean();
        long me = d.path("userId").asLong();
        List<Attendance> rows = attendance.values().stream()
                .filter(a -> a.deptId().equals(deptId))
                .filter(a -> !selfOnly || a.userId() == me)
                .toList();
        return ResponseEntity.ok(rows);
    }

    @PutMapping("/hr/attendance/{id}")
    public ResponseEntity<Object> editAttendance(@RequestHeader(value = "Authorization", required = false) String auth,
                                                 @PathVariable Long id, @RequestBody Map<String, Object> body) {
        Attendance a = attendance.get(id);
        if (a == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "考勤记录不存在"));
        }
        JsonNode d = check(auth, attendanceResource + ":edit", a.deptId());
        if (!allowed(d)) {
            return denied(d);
        }
        if (d.path("selfOnly").asBoolean() && a.userId() != d.path("userId").asLong()) {
            return selfOnly();
        }
        Attendance changed = new Attendance(a.id(), a.userId(), a.deptId(), a.date(), String.valueOf(body.get("status")));
        attendance.put(id, changed);
        return ResponseEntity.ok(changed);
    }

    // ------------------------------------------------------------------ 调权限中心

    /** 带着用户自己的令牌问权限中心。令牌无效时权限中心返回 401，这里原样转成拒绝。 */
    JsonNode check(String authorization, String permission, Long deptId) {
        if (authorization == null) {
            return null;
        }
        Map<String, Object> body = new HashMap<>();
        body.put("permission", permission);
        body.put("targetDeptId", deptId);
        JsonNode resp = center.post().uri("/authz/check")
                .header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> { })
                .body(JsonNode.class);
        return resp == null ? null : resp.path("data");
    }

    private static boolean allowed(JsonNode d) {
        return d != null && d.path("allowed").asBoolean();
    }

    private static ResponseEntity<Object> denied(JsonNode d) {
        if (d == null || d.isMissingNode()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "未登录或令牌无效"));
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("message", "权限中心拒绝", "reason", d.path("reason").asText()));
    }

    private static ResponseEntity<Object> selfOnly() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("message", "该权限只限本人记录", "reason", "SELF_ONLY"));
    }
}
