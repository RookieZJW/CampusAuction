-- ============================================================
-- 增量迁移：多校隔离（学校认证 + 拍品按校隔离）
-- 日期：2026-11-21
-- 适用：已执行过 sql/init.sql 的库；新库以 init.sql 为准（结构已同步）
--
-- 说明：
-- 1. 新建 t_school 学校字典表，预置 6 所河南高校
-- 2. t_user / t_item / t_auction 增加 school_id 字段
-- 3. 存量数据默认归河南理工大学（id=1），保证演示数据不丢
-- 4. 本脚本可重复执行（用 information_schema 判空，避免重复加列报错）
-- ============================================================

USE campus_auction;

-- ---------- 1) 学校字典表 ----------
CREATE TABLE IF NOT EXISTS t_school (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(64)  NOT NULL COMMENT '学校全称',
    short_name  VARCHAR(32)  NOT NULL COMMENT '简称，小程序展示用',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    sort        INT          NOT NULL DEFAULT 0 COMMENT '排序，越小越靠前',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_name (name)
) ENGINE = InnoDB COMMENT '学校字典';

-- 预置学校（INSERT IGNORE 保证重复执行不报错）
INSERT IGNORE INTO t_school (id, name, short_name, status, sort) VALUES
(1, '河南理工大学', '河南理工', 1, 1),
(2, '河南职业技术学院', '河南职院', 1, 2),
(3, '郑州大学', '郑大', 1, 3),
(4, '河南大学', '河大', 1, 4),
(5, '河南财经政法大学', '河南财大', 1, 5),
(6, '华北水利水电大学', '华水', 1, 6);

-- ---------- 2) t_user.school_id ----------
SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA='campus_auction' AND TABLE_NAME='t_user' AND COLUMN_NAME='school_id');
SET @sql := IF(@col=0,
    'ALTER TABLE t_user ADD COLUMN school_id BIGINT NULL COMMENT ''认证学校ID，NULL=未认证'' AFTER phone, ADD KEY idx_school (school_id)',
    'SELECT ''t_user.school_id 已存在，跳过'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 3) t_item.school_id ----------
SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA='campus_auction' AND TABLE_NAME='t_item' AND COLUMN_NAME='school_id');
SET @sql := IF(@col=0,
    'ALTER TABLE t_item ADD COLUMN school_id BIGINT NULL COMMENT ''发布时卖家所属学校，冗余免联表'' AFTER seller_id, ADD KEY idx_school (school_id)',
    'SELECT ''t_item.school_id 已存在，跳过'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 4) t_auction.school_id ----------
SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA='campus_auction' AND TABLE_NAME='t_auction' AND COLUMN_NAME='school_id');
SET @sql := IF(@col=0,
    'ALTER TABLE t_auction ADD COLUMN school_id BIGINT NULL COMMENT ''所属学校，列表按本校过滤'' AFTER seller_id, ADD KEY idx_school_status (school_id, status)',
    'SELECT ''t_auction.school_id 已存在，跳过'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 5) 存量数据归河南理工大学（id=1）----------
-- 仅在 school_id 为 NULL 时回填，重复执行不会覆盖用户后续认证结果
UPDATE t_user    SET school_id = 1 WHERE school_id IS NULL;
UPDATE t_item    SET school_id = 1 WHERE school_id IS NULL;
UPDATE t_auction SET school_id = 1 WHERE school_id IS NULL;

-- 迁移后自检
-- SELECT id, name, short_name FROM t_school;
-- SELECT COUNT(*) AS unassigned FROM t_auction WHERE school_id IS NULL;  -- 应为 0
-- SHOW COLUMNS FROM t_user LIKE 'school_id';
