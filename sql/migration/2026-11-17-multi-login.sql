-- ============================================================
-- 增量迁移：多方式登录（微信 / 手机号 / 邮箱）账号打通
-- 日期：2026-11-17
-- 适用：已执行过 sql/init.sql 的库；新库请以 init.sql 为准（已同步字段）
--
-- 说明：
-- 1. t_user.email 作为邮箱登录凭证；openid 仍是账号唯一锚点，不变
-- 2. phone / email 加唯一索引，保证一个凭证只指向一个账号
-- 3. MySQL 唯一索引允许多个 NULL，未绑定用户不受影响
-- ============================================================

USE campus_auction;

-- 邮箱字段（幂等：已存在则跳过，MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，故给出手工兜底注释）
-- 若报 Duplicate column name 'email'，说明已加过，忽略即可继续往下执行。
ALTER TABLE t_user
    ADD COLUMN email VARCHAR(128) NULL COMMENT '邮箱登录凭证，唯一' AFTER phone;

-- 唯一索引：若之前已存在同名索引，会报 Duplicate key name，忽略即可。
ALTER TABLE t_user ADD UNIQUE KEY uk_email (email);
ALTER TABLE t_user ADD UNIQUE KEY uk_phone (phone);

-- 迁移后自检：两条 SQL 都应返回 0 行
-- SELECT phone, COUNT(*) c FROM t_user WHERE phone IS NOT NULL GROUP BY phone HAVING c > 1;
-- SELECT email, COUNT(*) c FROM t_user WHERE email IS NOT NULL GROUP BY email HAVING c > 1;
