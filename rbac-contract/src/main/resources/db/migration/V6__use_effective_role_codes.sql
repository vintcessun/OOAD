-- Preserve built-in role IDs, codes, grants and assignments.
-- A reserved code without the corresponding old marker must not gain its qualification.
START TRANSACTION;

UPDATE sys_role r
LEFT JOIN sys_role alias
    ON alias.system_id = r.system_id AND alias.code = CONCAT('legacy-role-', r.id)
SET r.code = CASE
        WHEN alias.id IS NULL THEN CONCAT('legacy-role-', r.id)
        ELSE CONCAT('legacy-role-', r.id, '-', REPLACE(UUID(), '-', ''))
    END,
    r.version = r.version + 1
WHERE CAST(r.code AS BINARY) IN ('teacher', 'student', 'admin')
  AND NOT (CAST(r.identity_role AS BINARY) <=> CAST(r.code AS BINARY));

UPDATE sys_grant_version SET version = version + 1 WHERE id = 1;
COMMIT;
