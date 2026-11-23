package com.xmu.rbac.ddd.domain;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.*;

/** RBAC1 图规则的纯领域回归。 */
class RoleHierarchyTest {
    private Role role(long id) {
        return new Role(id, "s", "r" + id, "角色", null, true, false, 0, Set.of(id));
    }

    private RoleHierarchy graph() {
        return new RoleHierarchy(List.of(role(1), role(2), role(3), role(4)), Set.of());
    }

    @Test
    void direct() {
        assertThat(graph().paths(Set.of(1L))).containsExactly(List.of(1L));
    }

    @Test
    void oneEdge() {
        RoleHierarchy g = graph();
        g.link(1, 2);
        assertThat(g.paths(Set.of(1L))).contains(List.of(1L, 2L));
    }

    @Test
    void transitive() {
        RoleHierarchy g = graph();
        g.link(1, 2);
        g.link(2, 3);
        assertThat(g.paths(Set.of(1L))).contains(List.of(1L, 2L, 3L));
    }

    @Test
    void diamond() {
        RoleHierarchy g = graph();
        g.link(1, 2);
        g.link(1, 3);
        g.link(2, 4);
        g.link(3, 4);
        assertThat(g.paths(Set.of(1L))).contains(List.of(1L, 2L, 4L), List.of(1L, 3L, 4L));
    }

    @Test
    void duplicate() {
        RoleHierarchy g = graph();
        g.link(1, 2);
        assertThatThrownBy(() -> g.link(1, 2)).hasMessageContaining("ROLE_EDGE_EXISTS");
    }

    @Test
    void selfLoop() {
        assertThatThrownBy(() -> graph().link(1, 1)).hasMessageContaining("ROLE_CYCLE");
    }

    @Test
    void cycle() {
        RoleHierarchy g = graph();
        g.link(1, 2);
        g.link(2, 3);
        assertThatThrownBy(() -> g.link(3, 1)).hasMessageContaining("ROLE_CYCLE");
    }

    @Test
    void crossSystem() {
        Role other = new Role(5, "other", "x", "X", null, true, false, 0, Set.of());
        RoleHierarchy g = new RoleHierarchy(List.of(role(1), other), Set.of());
        assertThatThrownBy(() -> g.link(1, 5)).hasMessageContaining("CROSS_SYSTEM_ROLE");
    }

    @Test
    void disabledCutsPath() {
        Role b = role(2);
        b.changeStatus(false);
        RoleHierarchy g =
                new RoleHierarchy(
                        List.of(role(1), b, role(3)),
                        Set.of(new RoleHierarchy.Edge(1, 2), new RoleHierarchy.Edge(2, 3)));
        assertThat(g.paths(Set.of(1L))).containsExactly(List.of(1L));
    }

    @Test
    void alternatePath() {
        RoleHierarchy g = graph();
        g.link(1, 2);
        g.link(1, 3);
        g.link(3, 2);
        g.unlink(1, 2);
        assertThat(g.paths(Set.of(1L))).contains(List.of(1L, 3L, 2L));
    }

    @Test
    void inverseAfterFirstLink() {
        RoleHierarchy g = graph();
        g.link(1, 2);
        assertThatThrownBy(() -> g.link(2, 1)).isInstanceOf(DomainException.class);
    }

    @Test
    void multipleDirectRoles() {
        assertThat(graph().paths(Set.of(1L, 2L))).hasSize(2);
    }

    @Test
    void missingRole() {
        assertThatThrownBy(() -> graph().link(1, 99)).hasMessageContaining("ROLE_NOT_FOUND");
    }

    @Test
    void disabledDirectRole() {
        Role a = role(1);
        a.changeStatus(false);
        assertThat(new RoleHierarchy(List.of(a), Set.of()).paths(Set.of(1L))).isEmpty();
    }

    @Test
    void directAndInheritedSources() {
        RoleHierarchy g = graph();
        g.link(1, 2);
        assertThat(g.paths(Set.of(1L, 2L))).contains(List.of(2L), List.of(1L, 2L));
    }

    @Test
    void corruptedRestoredGraphCannotAuthorizeACycle() {
        assertThatThrownBy(
                        () ->
                                new RoleHierarchy(
                                        List.of(role(1), role(2)),
                                        Set.of(
                                                new RoleHierarchy.Edge(1, 2),
                                                new RoleHierarchy.Edge(2, 1))))
                .hasMessageContaining("ROLE_CYCLE");
    }

    @Test
    void corruptedRestoredGraphCannotCrossSystems() {
        Role other = new Role(2, "other", "r2", "角色", null, true, false, 0, Set.of());
        assertThatThrownBy(
                        () ->
                                new RoleHierarchy(
                                        List.of(role(1), other),
                                        Set.of(new RoleHierarchy.Edge(1, 2))))
                .hasMessageContaining("CROSS_SYSTEM_ROLE");
    }

    @Test
    void removeMissingEdgeDoesNotChangeGraph() {
        RoleHierarchy graph = graph();
        assertThatThrownBy(() -> graph.unlink(1, 2)).hasMessageContaining("ROLE_EDGE_NOT_FOUND");
        assertThat(graph.edges()).isEmpty();
    }
}
