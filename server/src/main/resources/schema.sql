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

-- ------------------------------------------------------------
-- 启动时幂等迁移：t_order 支付维度字段（方案 B 可插拔支付网关）
-- 与 sql/migration/2026-11-21-payment-gateway.sql 内容一致，可重复执行
-- ------------------------------------------------------------
SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='t_order' AND COLUMN_NAME='pay_status');
SET @sql := IF(@col=0,
    'ALTER TABLE t_order ADD COLUMN pay_status VARCHAR(20) NOT NULL DEFAULT ''UNPAID'' COMMENT ''UNPAID未支付 PAYING支付中 PAID已支付 CLOSED已关闭'' AFTER completed_at',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='t_order' AND COLUMN_NAME='pay_no');
SET @sql := IF(@col=0,
    'ALTER TABLE t_order ADD COLUMN pay_no VARCHAR(64) NULL COMMENT ''支付单号 PAY+时间戳+随机'' AFTER pay_status',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='t_order' AND COLUMN_NAME='pay_provider');
SET @sql := IF(@col=0,
    'ALTER TABLE t_order ADD COLUMN pay_provider VARCHAR(20) NULL COMMENT ''支付网关标识 MOCK/WXPAY'' AFTER pay_no',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='t_order' AND COLUMN_NAME='pay_amount');
SET @sql := IF(@col=0,
    'ALTER TABLE t_order ADD COLUMN pay_amount INT NULL COMMENT ''本次支付金额（分）'' AFTER pay_provider',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='t_order' AND COLUMN_NAME='transaction_id');
SET @sql := IF(@col=0,
    'ALTER TABLE t_order ADD COLUMN transaction_id VARCHAR(64) NULL COMMENT ''第三方交易号'' AFTER pay_amount',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='t_order' AND COLUMN_NAME='paid_at');
SET @sql := IF(@col=0,
    'ALTER TABLE t_order ADD COLUMN paid_at DATETIME NULL COMMENT ''支付成功时间'' AFTER transaction_id',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
             WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='t_order' AND INDEX_NAME='uk_pay_no');
SET @sql := IF(@idx=0,
    'ALTER TABLE t_order ADD UNIQUE KEY uk_pay_no (pay_no)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE t_order SET pay_status = 'UNPAID' WHERE pay_status IS NULL OR pay_status = '';
