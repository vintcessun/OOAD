-- Teacher 24a4d7d authority and menu vocabulary, scoped to the course system.
SET @course_system = (SELECT id FROM sys_system WHERE code = 'course-project-management');

INSERT INTO sys_permission (system_id, code, name, kind, status)
SELECT @course_system, expected.code, expected.name, expected.kind, 'ENABLED'
FROM (
    SELECT 'AV' AS code, '查看课程日志' AS name, 'API' AS kind
    UNION ALL
    SELECT 'TV', '查看教师', 'API'
    UNION ALL
    SELECT 'CTM', '管理课程团队', 'API'
    UNION ALL
    SELECT 'CC', '复制课程', 'API'
    UNION ALL
    SELECT 'UL', '查询人员', 'API'
    UNION ALL
    SELECT 'MCO', '课程', 'UI'
    UNION ALL
    SELECT 'MCOR', '审核课程', 'UI'
    UNION ALL
    SELECT 'MCOE', '编辑课程', 'UI'
    UNION ALL
    SELECT 'MCOD', '删除课程', 'UI'
    UNION ALL
    SELECT 'MCOT', '管理课程团队', 'UI'
    UNION ALL
    SELECT 'MTR', '学期', 'UI'
    UNION ALL
    SELECT 'MTRC', '新建学期', 'UI'
    UNION ALL
    SELECT 'MTRE', '编辑学期', 'UI'
    UNION ALL
    SELECT 'MTRD', '删除学期', 'UI'
    UNION ALL
    SELECT 'MTRM', '设置当前学期', 'UI'
    UNION ALL
    SELECT 'MTE', '教师', 'UI'
    UNION ALL
    SELECT 'MTEC', '新增教师', 'UI'
    UNION ALL
    SELECT 'MTEE', '编辑教师', 'UI'
    UNION ALL
    SELECT 'MTED', '删除教师', 'UI'
    UNION ALL
    SELECT 'MTEL', '查询人员', 'UI'
    UNION ALL
    SELECT 'MLG', '日志', 'UI'
) expected
LEFT JOIN sys_permission present ON present.system_id = @course_system
    AND present.kind = expected.kind AND present.code = expected.code
WHERE present.id IS NULL;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT administrator.id, permission.id
FROM sys_role administrator
JOIN sys_permission permission ON permission.system_id = administrator.system_id
LEFT JOIN sys_role_permission present
    ON present.role_id = administrator.id AND present.permission_id = permission.id
WHERE administrator.system_id = @course_system AND administrator.code = 'admin'
    AND ((permission.kind = 'API' AND permission.code IN ('AV','TV','CTM','CC','UL'))
      OR (permission.kind = 'UI' AND permission.code IN ('MCO','MCOR','MCOE','MCOD','MCOT','MTR','MTRC','MTRE','MTRD','MTRM','MTE','MTEC','MTEE','MTED','MTEL','MLG')))
    AND present.id IS NULL;

UPDATE sys_api_operation operation
JOIN (
    SELECT 'listAuditLogs' AS operation_id, 'AV' AS code
    UNION ALL
    SELECT 'listTeachers', 'TV'
    UNION ALL
    SELECT 'setCourseLeaders', 'CTM'
    UNION ALL
    SELECT 'setClassTeachers', 'CTM'
) expected ON expected.operation_id = operation.operation_id
JOIN sys_permission permission ON permission.system_id = operation.system_id
    AND permission.kind = 'API' AND permission.code = expected.code
SET operation.permission_id = permission.id, operation.version = operation.version + 1
WHERE operation.system_id = @course_system;

INSERT INTO sys_ui_resource (system_id, type, code, name, route, sort_order, status, permission_id)
SELECT @course_system, 'MENU', expected.code, permission.name, expected.route,
    expected.sort_order, 'ENABLED', permission.id
FROM (
    SELECT 'MCO' AS code, '/admin/courses' AS route, 100 AS sort_order
    UNION ALL
    SELECT 'MTR', '/admin/terms', 101
    UNION ALL
    SELECT 'MTE', '/admin/teachers', 102
    UNION ALL
    SELECT 'MLG', '/admin/audit', 103
) expected
JOIN sys_permission permission ON permission.system_id = @course_system
    AND permission.kind = 'UI' AND permission.code = expected.code;

INSERT INTO sys_ui_resource (system_id, parent_id, type, code, name, sort_order, status, permission_id)
SELECT @course_system, menu.id, 'BUTTON', expected.code, permission.name,
    expected.sort_order, 'ENABLED', permission.id
FROM (
    SELECT 'MCOR' AS code, 'MCO' AS menu_code, 1 AS sort_order
    UNION ALL
    SELECT 'MCOE', 'MCO', 2
    UNION ALL
    SELECT 'MCOD', 'MCO', 3
    UNION ALL
    SELECT 'MCOT', 'MCO', 4
    UNION ALL
    SELECT 'MTRC', 'MTR', 1
    UNION ALL
    SELECT 'MTRE', 'MTR', 2
    UNION ALL
    SELECT 'MTRD', 'MTR', 3
    UNION ALL
    SELECT 'MTRM', 'MTR', 4
    UNION ALL
    SELECT 'MTEC', 'MTE', 1
    UNION ALL
    SELECT 'MTEE', 'MTE', 2
    UNION ALL
    SELECT 'MTED', 'MTE', 3
    UNION ALL
    SELECT 'MTEL', 'MTE', 4
) expected
JOIN sys_ui_resource menu ON menu.system_id = @course_system AND menu.code = expected.menu_code
JOIN sys_permission permission ON permission.system_id = @course_system
    AND permission.kind = 'UI' AND permission.code = expected.code;

INSERT INTO sys_ui_api_mapping (ui_resource_id, api_operation_id)
SELECT resource.id, operation.id
FROM (
    SELECT 'MCO' AS ui_code, 'listAdminCourses' AS operation_id
    UNION ALL
    SELECT 'MCOR', 'reviewCourse'
    UNION ALL
    SELECT 'MCOE', 'updateCourse'
    UNION ALL
    SELECT 'MCOD', 'deleteCourse'
    UNION ALL
    SELECT 'MCOT', 'setCourseLeaders'
    UNION ALL
    SELECT 'MCOT', 'setClassTeachers'
    UNION ALL
    SELECT 'MTR', 'listTerms'
    UNION ALL
    SELECT 'MTRC', 'createTerm'
    UNION ALL
    SELECT 'MTRE', 'updateTerm'
    UNION ALL
    SELECT 'MTRD', 'deleteTerm'
    UNION ALL
    SELECT 'MTRM', 'replaceCurrentTerm'
    UNION ALL
    SELECT 'MTRM', 'clearCurrentTerm'
    UNION ALL
    SELECT 'MTE', 'listTeachers'
    UNION ALL
    SELECT 'MTEC', 'createTeacher'
    UNION ALL
    SELECT 'MTEE', 'updateTeacher'
    UNION ALL
    SELECT 'MTED', 'deleteTeacher'
    UNION ALL
    SELECT 'MTEL', 'lookupIdentityUser'
    UNION ALL
    SELECT 'MLG', 'listAuditLogs'
) expected
JOIN sys_ui_resource resource ON resource.system_id = @course_system AND resource.code = expected.ui_code
JOIN sys_api_operation operation ON operation.system_id = @course_system
    AND operation.operation_id = expected.operation_id;

UPDATE sys_grant_version SET version = version + 1 WHERE id = 1;
