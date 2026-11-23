-- Register department lifecycle capabilities without changing applied migrations.
START TRANSACTION;

INSERT INTO sys_permission (system_id, code, name, kind, status)
SELECT id, 'rbac:department:status', '启停部门', 'API', 'ENABLED' FROM sys_system WHERE code='rbac-center'
UNION ALL
SELECT id, 'rbac:department:delete', '删除部门', 'API', 'ENABLED' FROM sys_system WHERE code='rbac-center'
UNION ALL
SELECT id, 'rbac:ui:setDepartmentStatus', '启停部门', 'UI', 'ENABLED' FROM sys_system WHERE code='rbac-center';

UPDATE sys_api_operation a
JOIN sys_system s ON s.id=a.system_id AND s.code='rbac-center'
JOIN sys_permission p ON p.system_id=s.id AND p.kind='API' AND p.code='rbac:department:delete'
SET a.permission_id=p.id, a.version=a.version+1
WHERE a.operation_id='deleteDepartment';

INSERT INTO sys_api_operation (system_id, operation_id, method, path_template, permission_id, access_policy)
SELECT s.id, 'setDepartmentStatus', 'PUT', '/api/v1/departments/{id}/status', p.id, 'PROTECTED'
FROM sys_system s JOIN sys_permission p ON p.system_id=s.id AND p.kind='API' AND p.code='rbac:department:status'
WHERE s.code='rbac-center';

INSERT INTO sys_ui_resource (system_id, parent_id, type, code, name, sort_order, status, permission_id)
SELECT s.id, menu.id, 'BUTTON', p.code, '启停部门', menu.sort_order, 'ENABLED', p.id
FROM sys_system s
JOIN sys_ui_resource menu ON menu.system_id=s.id AND menu.code='rbac:ui:departments:view'
JOIN sys_permission p ON p.system_id=s.id AND p.kind='UI' AND p.code='rbac:ui:setDepartmentStatus'
WHERE s.code='rbac-center';

INSERT INTO sys_ui_api_mapping (ui_resource_id, api_operation_id)
SELECT ui.id, a.id FROM sys_system s
JOIN sys_ui_resource ui ON ui.system_id=s.id AND ui.code='rbac:ui:setDepartmentStatus'
JOIN sys_api_operation a ON a.system_id=s.id AND a.operation_id='setDepartmentStatus'
WHERE s.code='rbac-center';

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_system s
JOIN sys_role r ON r.system_id=s.id AND r.builtin=TRUE AND r.code IN ('SUPER_ADMIN','RBAC_ADMIN')
JOIN sys_permission p ON p.system_id=s.id AND (
    (p.kind='API' AND p.code IN ('rbac:department:status','rbac:department:delete'))
    OR (p.kind='UI' AND p.code='rbac:ui:setDepartmentStatus'))
WHERE s.code='rbac-center';

UPDATE sys_grant_version SET version=version+1 WHERE id=1;

COMMIT;
