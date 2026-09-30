package com.xmu.rbac.mockbiz;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 两个外部模拟系统的路由，与 rbac-api.yaml 中 biz 标签的接口一一对应。
 * 待实现：每个接口先调权限中心 /authz/check（subject = 当前用户，resource/action = 下方注释的权限码），
 * 允许才执行，拒绝返回 403。
 */
@RestController
public class MockBizController {

    private static ResponseEntity<Map<String, Object>> todo(String permission) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(Map.of("code", 10006, "message", "骨架：待实现（需要 " + permission + "）"));
    }

    @GetMapping("/oa/documents")
    public ResponseEntity<Map<String, Object>> listDocuments() {
        return todo("oa:doc:draft:view");
    }

    @PostMapping("/oa/documents")
    public ResponseEntity<Map<String, Object>> createDocument() {
        return todo("oa:doc:draft:create");
    }

    @PostMapping("/oa/documents/{id}/approve")
    public ResponseEntity<Map<String, Object>> approveDocument(@PathVariable Long id) {
        return todo("oa:doc:draft:approve");
    }

    @GetMapping("/hr/attendance")
    public ResponseEntity<Map<String, Object>> listAttendance() {
        return todo("hr:attendance:record:view");
    }

    @PutMapping("/hr/attendance/{id}")
    public ResponseEntity<Map<String, Object>> editAttendance(@PathVariable Long id) {
        return todo("hr:attendance:record:edit");
    }
}
