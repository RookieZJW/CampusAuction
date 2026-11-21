-- ============================================================
-- 一次性修复：t_school 学校名乱码（2026-11-21）
-- 原因：初次执行 2026-11-21-school-isolation.sql 时 mysql 客户端
--       未指定 utf8mb4，Windows 默认 GBK 导致 UTF-8 中文被二次编码。
-- 执行方式（必须带字符集参数）：
--   mysql --default-character-set=utf8mb4 -uroot -p campus_auction < 本文件
-- 幂等：重复执行结果一致。
-- ============================================================

SET NAMES utf8mb4;
USE campus_auction;

UPDATE t_school SET name = '河南理工大学',       short_name = '河南理工' WHERE id = 1;
UPDATE t_school SET name = '河南职业技术学院',   short_name = '河南职院' WHERE id = 2;
UPDATE t_school SET name = '郑州大学',           short_name = '郑大'     WHERE id = 3;
UPDATE t_school SET name = '河南大学',           short_name = '河大'     WHERE id = 4;
UPDATE t_school SET name = '河南财经政法大学',   short_name = '河南财大' WHERE id = 5;
UPDATE t_school SET name = '华北水利水电大学',   short_name = '华水'     WHERE id = 6;

-- 自检：应显示正确中文
-- SELECT id, name, short_name FROM t_school ORDER BY id;
