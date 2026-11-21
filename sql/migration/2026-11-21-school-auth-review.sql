-- ============================================================
-- 增量迁移：学校认证改为「学生证 + 管理员审核」
-- 日期：2026-11-21
-- 适用：已执行过 2026-11-21-school-isolation.sql 的库
-- 执行：mysql --default-character-set=utf8mb4 -uroot -p campus_auction < 本文件
--
-- 说明：
-- 1. 新建 t_school_auth 认证申请表（学生证照片 + 姓名学号 + 审核状态）
-- 2. t_user 增加 auth_status（NONE/PENDING/APPROVED/REJECTED）
-- 3. 存量已认证用户（school_id 非空）标记为 APPROVED，保持演示数据可用
-- 4. 幂等：判空加列、CREATE TABLE IF NOT EXISTS，可重复执行
-- ============================================================

SET NAMES utf8mb4;
USE campus_auction;

-- 1) 认证申请表
CREATE TABLE IF NOT EXISTS t_school_auth (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT       NOT NULL COMMENT '申请人 user_id',
    school_id       BIGINT       NOT NULL COMMENT '申请学校',
    real_name       VARCHAR(32)  NOT NULL COMMENT '真实姓名',
    student_no      VARCHAR(32)  NOT NULL COMMENT '学号',
    student_card    VARCHAR(512) NOT NULL COMMENT '学生证照片 URL',
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                    COMMENT 'PENDING待审 APPROVED通过 REJECTED驳回',
    reject_reason   VARCHAR(255) NULL COMMENT '驳回理由',
    reviewer_id     BIGINT       NULL COMMENT '审核管理员 id',
    review_time     DATETIME     NULL,
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_user (user_id),
    KEY idx_status (status)
) ENGINE = InnoDB COMMENT '学校认证申请';

-- 2) t_user.auth_status
SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA='campus_auction' AND TABLE_NAME='t_user' AND COLUMN_NAME='auth_status');
SET @sql := IF(@col=0,
    'ALTER TABLE t_user ADD COLUMN auth_status VARCHAR(20) NOT NULL DEFAULT ''NONE'' COMMENT ''认证状态 NONE/PENDING/APPROVED/REJECTED'' AFTER school_id',
    'SELECT ''t_user.auth_status 已存在，跳过'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3) 存量已认证用户标记为 APPROVED（保持此前迁移归河南理工的演示账号可用）
UPDATE t_user SET auth_status = 'APPROVED' WHERE school_id IS NOT NULL AND auth_status = 'NONE';

-- 自检
-- SELECT auth_status, COUNT(*) FROM t_user GROUP BY auth_status;
-- SELECT COUNT(*) FROM t_school_auth;
