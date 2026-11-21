-- 启动时幂等迁移：学生认证资料持久化到 t_user
-- 与 sql/migration/2026-11-21-user-auth-persist.sql 内容一致，可重复执行
SET NAMES utf8mb4;

SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='t_user' AND COLUMN_NAME='real_name');
SET @sql := IF(@col=0,
    'ALTER TABLE t_user ADD COLUMN real_name VARCHAR(32) NULL COMMENT ''认证真实姓名'' AFTER auth_status',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='t_user' AND COLUMN_NAME='student_no');
SET @sql := IF(@col=0,
    'ALTER TABLE t_user ADD COLUMN student_no VARCHAR(32) NULL COMMENT ''认证学号'' AFTER real_name',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='t_user' AND COLUMN_NAME='student_card');
SET @sql := IF(@col=0,
    'ALTER TABLE t_user ADD COLUMN student_card VARCHAR(512) NULL COMMENT ''认证学生证照片URL'' AFTER student_no',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='t_user' AND COLUMN_NAME='auth_time');
SET @sql := IF(@col=0,
    'ALTER TABLE t_user ADD COLUMN auth_time DATETIME NULL COMMENT ''认证通过时间'' AFTER student_card',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

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
