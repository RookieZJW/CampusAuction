-- ============================================================
-- 增量迁移：登录改为「微信 + 手机号」两方式，删除邮箱登录
-- 日期：2026-11-17（最终版，替换早前的 multi-login 草稿）
-- 适用：已执行过 sql/init.sql 的库；新库以 init.sql 为准（字段已同步）
--
-- 说明：
-- 1. phone 加唯一索引（一个手机号只指向一个账号）
-- 2. 删除 email 列及其索引（邮箱登录已彻底移除）
-- 3. 本脚本幂等：email 列/索引不存在时对应语句会报"不存在"，忽略即可
-- ============================================================

USE campus_auction;

-- 1) 手机号唯一索引（若报 Duplicate key name 'uk_phone'，说明已存在，忽略）
ALTER TABLE t_user ADD UNIQUE KEY uk_phone (phone);

-- 2) 删除邮箱唯一索引与列（若报 "index/column not exists"，说明已删过，忽略）
ALTER TABLE t_user DROP INDEX uk_email;
ALTER TABLE t_user DROP COLUMN email;

-- 迁移后自检：应返回 0 行
-- SELECT phone, COUNT(*) c FROM t_user WHERE phone IS NOT NULL GROUP BY phone HAVING c > 1;
-- SHOW COLUMNS FROM t_user;  -- 不应再看到 email 列
