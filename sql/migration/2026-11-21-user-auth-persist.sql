-- ============================================================
-- 增量迁移：学生认证信息持久化到 t_user
-- 日期：2026-11-21
-- 适用：已执行过 2026-11-21-school-auth-review.sql 的库
-- 执行：mysql --default-character-set=utf8mb4 -uroot -p campus_auction < 本文件
--
-- 说明：
-- 1. t_user 增加 real_name / student_no / student_card / auth_time
-- 2. 从 t_school_auth 的最新一条申请回填存量数据
-- 3. t_school_auth 保留作为审核凭证（审核人/驳回理由/审核时间），不再作为用户资料的唯一来源
-- 4. 幂等：列存在则跳过，回填只覆盖空值
-- ============================================================

SET NAMES utf8mb4;
USE campus_auction;

-- 1) 加列（逐列判空，可重复执行）
SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA='campus_auction' AND TABLE_NAME='t_user' AND COLUMN_NAME='real_name');
SET @sql := IF(@col=0,
    'ALTER TABLE t_user ADD COLUMN real_name VARCHAR(32) NULL COMMENT ''认证真实姓名'' AFTER auth_status',
    'SELECT ''t_user.real_name 已存在，跳过'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA='campus_auction' AND TABLE_NAME='t_user' AND COLUMN_NAME='student_no');
SET @sql := IF(@col=0,
    'ALTER TABLE t_user ADD COLUMN student_no VARCHAR(32) NULL COMMENT ''认证学号'' AFTER real_name',
    'SELECT ''t_user.student_no 已存在，跳过'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA='campus_auction' AND TABLE_NAME='t_user' AND COLUMN_NAME='student_card');
SET @sql := IF(@col=0,
    'ALTER TABLE t_user ADD COLUMN student_card VARCHAR(512) NULL COMMENT ''认证学生证照片URL'' AFTER student_no',
    'SELECT ''t_user.student_card 已存在，跳过'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA='campus_auction' AND TABLE_NAME='t_user' AND COLUMN_NAME='auth_time');
SET @sql := IF(@col=0,
    'ALTER TABLE t_user ADD COLUMN auth_time DATETIME NULL COMMENT ''认证通过时间'' AFTER student_card',
    'SELECT ''t_user.auth_time 已存在，跳过'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) 回填：取每个用户最新一条申请，只填空值（不覆盖已有持久化数据）
UPDATE t_user u
JOIN (
    SELECT a.user_id, a.real_name, a.student_no, a.student_card, a.status, a.review_time
    FROM t_school_auth a
    JOIN (SELECT user_id, MAX(id) AS max_id FROM t_school_auth GROUP BY user_id) x
      ON a.id = x.max_id
) a ON a.user_id = u.id
SET u.real_name    = IFNULL(u.real_name, a.real_name),
    u.student_no   = IFNULL(u.student_no, a.student_no),
    u.student_card = IFNULL(u.student_card, a.student_card),
    u.auth_time    = IFNULL(u.auth_time, IF(a.status = 'APPROVED', a.review_time, NULL))
WHERE u.real_name IS NULL OR u.student_no IS NULL OR u.student_card IS NULL OR u.auth_time IS NULL;

-- 自检
-- SELECT auth_status, COUNT(*) FROM t_user GROUP BY auth_status;
-- SELECT COUNT(*) FROM t_user WHERE real_name IS NOT NULL;
