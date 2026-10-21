-- =============================================================================
-- V4 取消账号到期时间（docs/03-database.md §3.2，docs/12-design-log.md R-21）
--
-- 课程要求账号只有启用、停用、删除三种状态，老师明确不要「到期」：临时用户用完由管理员手工停用。
-- 删列前先把已经到期、仍为启用的账号改为停用并写明原因，避免删列后它们重新可以登录。
-- =============================================================================

UPDATE sys_user
   SET status = 'DISABLED',
       status_reason = '账号到期（V4 取消到期时间时自动停用）'
 WHERE status = 'ACTIVE'
   AND expires_at IS NOT NULL
   AND expires_at <= NOW();

ALTER TABLE sys_user DROP COLUMN expires_at;
