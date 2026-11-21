-- ------------------------------------------------------------
-- 支付网关方案 B：t_order 增加支付维度字段（幂等，可重复执行）
-- 设计要点：
--   1. 支付是独立维度，不改动现有 status（PENDING_TRADING/COMPLETED/CANCELLED）
--   2. pay_status 四态：UNPAID / PAYING / PAID / CLOSED
--   3. pay_no 唯一索引，作为回调幂等兜底
--   4. 存量订单默认 UNPAID，不影响历史演示数据
-- 使用：mysql --default-character-set=utf8mb4 -u root -p campus_auction < 本文件
-- ------------------------------------------------------------
SET NAMES utf8mb4;

-- pay_status
SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='t_order' AND COLUMN_NAME='pay_status');
SET @sql := IF(@col=0,
    'ALTER TABLE t_order ADD COLUMN pay_status VARCHAR(20) NOT NULL DEFAULT ''UNPAID'' COMMENT ''UNPAID未支付 PAYING支付中 PAID已支付 CLOSED已关闭'' AFTER completed_at',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- pay_no
SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='t_order' AND COLUMN_NAME='pay_no');
SET @sql := IF(@col=0,
    'ALTER TABLE t_order ADD COLUMN pay_no VARCHAR(64) NULL COMMENT ''支付单号 PAY+时间戳+随机'' AFTER pay_status',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- pay_provider
SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='t_order' AND COLUMN_NAME='pay_provider');
SET @sql := IF(@col=0,
    'ALTER TABLE t_order ADD COLUMN pay_provider VARCHAR(20) NULL COMMENT ''支付网关标识 MOCK/WXPAY'' AFTER pay_no',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- pay_amount
SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='t_order' AND COLUMN_NAME='pay_amount');
SET @sql := IF(@col=0,
    'ALTER TABLE t_order ADD COLUMN pay_amount INT NULL COMMENT ''本次支付金额（分）'' AFTER pay_provider',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- transaction_id
SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='t_order' AND COLUMN_NAME='transaction_id');
SET @sql := IF(@col=0,
    'ALTER TABLE t_order ADD COLUMN transaction_id VARCHAR(64) NULL COMMENT ''第三方交易号'' AFTER pay_amount',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- paid_at
SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='t_order' AND COLUMN_NAME='paid_at');
SET @sql := IF(@col=0,
    'ALTER TABLE t_order ADD COLUMN paid_at DATETIME NULL COMMENT ''支付成功时间'' AFTER transaction_id',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- uk_pay_no 唯一索引（回调幂等兜底）
SET @idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
             WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='t_order' AND INDEX_NAME='uk_pay_no');
SET @sql := IF(@idx=0,
    'ALTER TABLE t_order ADD UNIQUE KEY uk_pay_no (pay_no)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 存量订单补齐默认值（幂等）
UPDATE t_order SET pay_status = 'UNPAID' WHERE pay_status IS NULL OR pay_status = '';
