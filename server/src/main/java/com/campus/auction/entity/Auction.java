package com.campus.auction.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 拍卖场次（规则与状态）
 * 状态流转：PENDING(随拍品待审) → ONGOING(审核通过即开拍) → FINISHED / FAILED / CANCELLED
 * W3/W4 接入竞价与结算后补充 SETTLING
 */
@Data
@TableName("t_auction")
public class Auction {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_ONGOING = "ONGOING";
    public static final String STATUS_FINISHED = "FINISHED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    /** 防狙击参数：结束前 5 分钟内有出价则延长 2 分钟，最多 3 次 */
    public static final int EXTEND_WINDOW_MS = 5 * 60 * 1000;
    public static final int EXTEND_DURATION_MS = 2 * 60 * 1000;
    public static final int MAX_EXTEND_COUNT = 3;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long itemId;

    private Long sellerId;

    private Integer startPrice;

    @TableField("`increment`")
    private Integer increment;

    private Integer buyoutPrice;

    private Integer deposit;

    /** 拍卖时长（分钟），审核通过后从开拍时刻起算 */
    private Integer durationMinutes;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private LocalDateTime originalEndTime;

    private Integer extendCount;

    private Integer currentPrice;

    private Long currentWinnerId;

    private Integer bidCount;

    private String status;

    /** 乐观锁版本号，出价/W3 更新时 WHERE version=? */
    private Integer version;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
