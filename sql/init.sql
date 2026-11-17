-- ============================================================
-- 校园在线拍卖系统 数据库初始化脚本
-- MySQL 8.0 / utf8mb4
-- 金额单位一律为「分」（int），禁用浮点
-- ============================================================

CREATE DATABASE IF NOT EXISTS campus_auction
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE campus_auction;

-- ------------------------------------------------------------
-- 1. 学生用户表
-- ------------------------------------------------------------
CREATE TABLE t_user (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    openid       VARCHAR(64)  NOT NULL COMMENT '微信 openid',
    nickname     VARCHAR(64)  NOT NULL DEFAULT '微信用户',
    avatar       VARCHAR(512) NULL,
    phone        VARCHAR(20)  NULL COMMENT '手机号登录凭证，唯一',
    email        VARCHAR(128) NULL COMMENT '邮箱登录凭证，唯一',
    credit_score INT          NOT NULL DEFAULT 100 COMMENT '信用分，初始100，低于60限制出价',
    balance      INT          NOT NULL DEFAULT 0 COMMENT '虚拟余额（分）',
    status       TINYINT      NOT NULL DEFAULT 0 COMMENT '0正常 1封禁',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_openid (openid),
    UNIQUE KEY uk_phone (phone),
    UNIQUE KEY uk_email (email)
) ENGINE = InnoDB COMMENT '学生用户';

-- ------------------------------------------------------------
-- 2. 管理员表
-- ------------------------------------------------------------
CREATE TABLE t_admin (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(32)  NOT NULL COMMENT '登录名',
    password_hash VARCHAR(128) NOT NULL COMMENT 'SHA-256(password)',
    role          TINYINT      NOT NULL DEFAULT 1 COMMENT '0超级管理员 1普通管理员',
    status        TINYINT      NOT NULL DEFAULT 0 COMMENT '0正常 1禁用',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_username (username)
) ENGINE = InnoDB COMMENT '管理员';

-- ------------------------------------------------------------
-- 3. 分类表（一级分类即可支撑毕设，预留 parent_id）
-- ------------------------------------------------------------
CREATE TABLE t_category (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(32) NOT NULL COMMENT '分类名',
    parent_id  BIGINT      NOT NULL DEFAULT 0 COMMENT '父分类，0=一级',
    sort       INT         NOT NULL DEFAULT 0 COMMENT '排序，越小越靠前',
    icon       VARCHAR(512) NULL,
    create_time DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_parent (parent_id)
) ENGINE = InnoDB COMMENT '拍品分类';

-- ------------------------------------------------------------
-- 4. 拍品表（物品静态信息，与拍卖场次解耦）
-- ------------------------------------------------------------
CREATE TABLE t_item (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    seller_id       BIGINT      NOT NULL COMMENT '卖家 user_id',
    category_id     BIGINT      NOT NULL,
    title           VARCHAR(100) NOT NULL,
    description     TEXT         NULL,
    images          JSON         NULL COMMENT '图片 URL 数组，最多3张',
    `condition`     VARCHAR(20)  NOT NULL DEFAULT '九成新' COMMENT '成色',
    ai_suggest_min  INT          NULL COMMENT 'AI 建议起拍价下限（分）',
    ai_suggest_max  INT          NULL COMMENT 'AI 建议起拍价上限（分）',
    status          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT'
                    COMMENT 'DRAFT草稿 PENDING待审 REJECTED已驳回 ON_SALE已上架 OFF_SALE已下架',
    reject_reason   VARCHAR(255) NULL COMMENT '驳回理由',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_seller (seller_id),
    KEY idx_category (category_id),
    KEY idx_status (status)
) ENGINE = InnoDB COMMENT '拍品（物品本体）';

-- ------------------------------------------------------------
-- 5. 拍卖场次表（规则与状态，核心表）
-- ------------------------------------------------------------
CREATE TABLE t_auction (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    item_id             BIGINT      NOT NULL COMMENT '关联 t_item',
    seller_id           BIGINT      NOT NULL COMMENT '冗余卖家ID，免联表',
    start_price         INT         NOT NULL COMMENT '起拍价（分）',
    `increment`         INT         NOT NULL DEFAULT 100 COMMENT '加价幅度（分）',
    buyout_price        INT         NULL COMMENT '一口价，NULL=无一口价',
    deposit             INT         NOT NULL DEFAULT 1000 COMMENT '参拍保证金（分）',
    duration_minutes    INT         NOT NULL DEFAULT 1440 COMMENT '拍卖时长(分钟)，审核通过后从开拍时刻起算',
    start_time          DATETIME    NULL COMMENT '开拍时间，审核通过时写入',
    end_time            DATETIME    NULL COMMENT '当前结束时间（可能被延时修改）',
    original_end_time   DATETIME    NULL COMMENT '原始结束时间，防狙击分析用',
    extend_count        INT         NOT NULL DEFAULT 0 COMMENT '已延时次数，上限3',
    current_price       INT         NOT NULL COMMENT '当前最高价（分）',
    current_winner_id   BIGINT      NULL COMMENT '当前领先者 user_id',
    bid_count           INT         NOT NULL DEFAULT 0,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                        COMMENT 'PENDING待审 READY待开始 ONGOING进行中 SETTLING结算中 FINISHED已成交 FAILED流拍 CANCELLED已取消',
    version             INT         NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    create_time         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_item (item_id),
    KEY idx_seller (seller_id),
    KEY idx_status_end (status, end_time) COMMENT '结算任务扫描索引'
) ENGINE = InnoDB COMMENT '拍卖场次';

-- ------------------------------------------------------------
-- 6. 出价流水表
-- ------------------------------------------------------------
CREATE TABLE t_bid_record (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    auction_id  BIGINT      NOT NULL,
    user_id     BIGINT      NOT NULL,
    price       INT         NOT NULL COMMENT '出价金额（分）',
    bid_time    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status      VARCHAR(20) NOT NULL DEFAULT 'LEADING'
                COMMENT 'LEADING领先 OUTBID被超越 WINNING中标 CANCELLED取消',
    KEY idx_auction_price (auction_id, price DESC),
    KEY idx_user (user_id)
) ENGINE = InnoDB COMMENT '出价流水';

-- ------------------------------------------------------------
-- 7. 订单表（auction_id 唯一索引保证结算幂等）
-- ------------------------------------------------------------
CREATE TABLE t_order (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no     VARCHAR(32)  NOT NULL COMMENT '订单号 AU+时间戳+随机',
    auction_id   BIGINT       NOT NULL,
    item_id      BIGINT       NOT NULL,
    buyer_id     BIGINT       NOT NULL COMMENT '中标者',
    seller_id    BIGINT       NOT NULL,
    deal_price   INT          NOT NULL COMMENT '成交价（分）',
    status       VARCHAR(20)  NOT NULL DEFAULT 'PENDING_TRADING'
                 COMMENT 'PENDING_TRADING待交割 COMPLETED已完成 CANCELLED已取消',
    trade_type   VARCHAR(20)  NOT NULL DEFAULT 'OFFLINE' COMMENT 'OFFLINE线下面交',
    trade_place  VARCHAR(255) NULL COMMENT '约定交割地点',
    completed_at DATETIME     NULL,
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_order_no (order_no),
    UNIQUE KEY uk_auction (auction_id) COMMENT '结算幂等：一场拍卖最多生成一单'
) ENGINE = InnoDB COMMENT '成交订单';

-- ------------------------------------------------------------
-- 8. 虚拟余额流水表
-- ------------------------------------------------------------
CREATE TABLE t_account_log (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT      NOT NULL,
    type          VARCHAR(20) NOT NULL
                  COMMENT 'GIFT赠送 FREEZE冻结 UNFREEZE解冻 DEDUCT扣除 REFUND退还',
    amount        INT         NOT NULL COMMENT '变动金额（分），冻结为负、解冻为正',
    balance_after INT         NOT NULL COMMENT '变动后余额（对账用）',
    ref_type      VARCHAR(20) NULL COMMENT '关联业务类型 AUCTION/ORDER',
    ref_id        BIGINT      NULL,
    remark        VARCHAR(255) NULL,
    create_time   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_user (user_id, create_time)
) ENGINE = InnoDB COMMENT '虚拟余额流水';

-- ------------------------------------------------------------
-- 9. 信用分流水表
-- ------------------------------------------------------------
CREATE TABLE t_credit_log (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT      NOT NULL,
    change_value INT         NOT NULL COMMENT '变动值，正加负减',
    reason       VARCHAR(64) NOT NULL,
    ref_id       BIGINT      NULL,
    create_time  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_user (user_id)
) ENGINE = InnoDB COMMENT '信用分流水';

-- ------------------------------------------------------------
-- 10. 互评表
-- ------------------------------------------------------------
CREATE TABLE t_review (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id    BIGINT      NOT NULL,
    from_user   BIGINT      NOT NULL,
    to_user     BIGINT      NOT NULL,
    rating      TINYINT     NOT NULL COMMENT '1-5星',
    content     VARCHAR(500) NULL,
    tags        VARCHAR(255) NULL COMMENT '评价标签，逗号分隔',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_order_from (order_id, from_user) COMMENT '一单一人只能评一次',
    KEY idx_to_user (to_user)
) ENGINE = InnoDB COMMENT '订单互评';

-- ------------------------------------------------------------
-- 11. 举报表
-- ------------------------------------------------------------
CREATE TABLE t_report (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    reporter_id   BIGINT       NOT NULL,
    target_type   VARCHAR(20)  NOT NULL COMMENT 'ITEM拍品 USER用户 BID出价',
    target_id     BIGINT       NOT NULL,
    reason        VARCHAR(255) NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING待处理 RESOLVED已核实 DISMISSED已驳回',
    handler_id    BIGINT       NULL COMMENT '处理管理员',
    handle_result VARCHAR(255) NULL,
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_status (status)
) ENGINE = InnoDB COMMENT '举报';

-- ------------------------------------------------------------
-- 12. 收藏表
-- ------------------------------------------------------------
CREATE TABLE t_favorite (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    auction_id  BIGINT       NOT NULL,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_auction (user_id, auction_id)
) ENGINE = InnoDB COMMENT '收藏';

-- ------------------------------------------------------------
-- 13. 站内消息表
-- ------------------------------------------------------------
CREATE TABLE t_notification (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    type        VARCHAR(20)  NOT NULL COMMENT 'AUCTION_WIN成交 AUCTION_LOSE被超越 AUDIT_RESULT审核 ITEM_REMIND开拍提醒 SYSTEM系统',
    title       VARCHAR(64)  NOT NULL,
    content     VARCHAR(500) NULL,
    ref_id      BIGINT       NULL,
    is_read     TINYINT      NOT NULL DEFAULT 0,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_user_read (user_id, is_read)
) ENGINE = InnoDB COMMENT '站内消息';

-- ------------------------------------------------------------
-- 14. 开拍提醒订阅表
-- ------------------------------------------------------------
CREATE TABLE t_subscribe (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT      NOT NULL,
    auction_id  BIGINT      NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'SUBSCRIBED' COMMENT 'SUBSCRIBED已订阅 SENT已发送 CANCELED已取消',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_auction (user_id, auction_id)
) ENGINE = InnoDB COMMENT '开拍提醒订阅';

-- ============================================================
-- 初始数据
-- ============================================================

-- 管理员：admin / admin123（密码为 SHA-256('admin123')，后台登录 W4 实现）
INSERT INTO t_admin (username, password_hash, role) VALUES
('admin', SHA2('admin123', 256), 0);

-- 一级分类
INSERT INTO t_category (name, parent_id, sort) VALUES
('教材书籍', 0, 1),
('数码电子', 0, 2),
('生活用品', 0, 3),
('运动器材', 0, 4),
('服饰鞋包', 0, 5),
('乐器', 0, 6),
('其他', 0, 7);
