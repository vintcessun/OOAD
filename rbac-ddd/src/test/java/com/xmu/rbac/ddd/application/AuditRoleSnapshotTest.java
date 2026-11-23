package com.xmu.rbac.ddd.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmu.rbac.ddd.domain.*;
import com.xmu.rbac.ddd.domain.port.*;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.*;

/** 审计角色是在写入时确定的不可变课程角色快照，不在读取历史时重算。 */
class AuditRoleSnapshotTest {
    @Test
    void successAndDenialCaptureCourseRoleAndNeverChangeAfterRevocation() {
        AuthorizationRepository grants = mock(AuthorizationRepository.class);
        AuditRepository repository = mock(AuditRepository.class);
        when(repository.append(any())).thenAnswer(i -> i.getArgument(0));
        Role teacher =
                new Role(
                        4,
                        "course-project-management",
                        "teacher",
                        "教师",
                        null,
                        true,
                        false,
                        0,
                        Set.of());
        when(grants.systemEnabled("course-project-management")).thenReturn(true);
        when(grants.assignments(7, "course-project-management")).thenReturn(Set.of(4L));
        when(grants.hierarchy("course-project-management"))
                .thenReturn(new RoleHierarchy(List.of(teacher), Set.of()));
        when(grants.permissions("course-project-management")).thenReturn(List.of());
        AuditApplicationService audits =
                new AuditApplicationService(
                        repository,
                        grants,
                        new ObjectMapper(),
                        mock(PlatformTransactionManager.class));
        AuthorizationRepository.Identity actor =
                new AuthorizationRepository.Identity(
                        7, "u7", "u7", "用户", "7", "unused", 1, User.Status.ACTIVE, 0);
        AuditFact success =
                audits.local(actor, "course-project-management", "CHANGE", "目标", "SUCCESS", null);
        AuditFact denied =
                audits.local(
                        actor,
                        "course-project-management",
                        "DENY",
                        "目标",
                        "DENIED",
                        "PERMISSION_DENIED");
        teacher.changeStatus(false);
        assertThat(success.actorRoles()).containsExactly("teacher");
        assertThat(denied.actorRoles()).containsExactly("teacher");
        assertThat(
                        audits.local(
                                        actor,
                                        "course-project-management",
                                        "CHANGE",
                                        "目标",
                                        "SUCCESS",
                                        null)
                                .actorRoles())
                .isEmpty();
        assertThatThrownBy(() -> success.actorRoles().add("admin"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void disabledUnknownAndUnavailableSystemsDoNotInventRoleSnapshots() {
        AuthorizationRepository grants = mock(AuthorizationRepository.class);
        AuditRepository repository = mock(AuditRepository.class);
        when(repository.append(any())).thenAnswer(i -> i.getArgument(0));
        AuditApplicationService audits =
                new AuditApplicationService(
                        repository,
                        grants,
                        new ObjectMapper(),
                        mock(PlatformTransactionManager.class));
        AuthorizationRepository.Identity actor =
                new AuthorizationRepository.Identity(
                        7, "u7", "u7", "用户", "7", "unused", 1, User.Status.ACTIVE, 0);
        when(grants.systemEnabled("missing"))
                .thenThrow(new DomainException(404, "SYSTEM_NOT_FOUND"));
        assertThat(audits.local(actor, "missing", "DENY", "目标", "DENIED", null).actorRoles())
                .isEmpty();
        assertThat(audits.local(actor, "disabled", "DENY", "目标", "DENIED", null).actorRoles())
                .isEmpty();
        when(grants.systemEnabled("offline")).thenThrow(new DomainException(503, "DB_UNAVAILABLE"));
        assertThatThrownBy(() -> audits.local(actor, "offline", "DENY", "目标", "DENIED", null))
                .hasMessageContaining("DB_UNAVAILABLE");
        AuthorizationRepository.Identity disabled =
                new AuthorizationRepository.Identity(
                        7, "u7", "u7", "用户", "7", "unused", 1, User.Status.DISABLED, 0);
        assertThat(audits.local(disabled, "offline", "DENY", "目标", "DENIED", null).actorRoles())
                .isEmpty();
        assertThat(audits.local(null, "offline", "DENY", "目标", "DENIED", null).actorRoles())
                .isEmpty();
    }
}
