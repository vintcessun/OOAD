package com.xmu.rbac.contract;

import java.util.HashSet;
import java.util.Set;

/** Role codes understood by the course identity and audit contracts. */
public final class CourseRoleCodes {
    public static final Set<String> ALL = Set.of("student", "teacher", "admin");

    private CourseRoleCodes() {}

    public static Set<String> retainFrom(Set<String> roleCodes) {
        Set<String> result = new HashSet<>(roleCodes);
        result.retainAll(ALL);
        return Set.copyOf(result);
    }
}
