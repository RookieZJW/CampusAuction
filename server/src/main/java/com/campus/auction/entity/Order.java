package com.campus.auction.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 成交订单
 * status: PENDING_TRADING待交割 COMPLETED已完成 CANCELLED已取消
 * auction_id 唯一索引保证结算幂等：一场拍卖最多生成一单
 */
@Data
@TableName("t_order")
public class Order {

    public static final String STATUS_PENDING_TRADING = "PENDING_TRADING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    private Long auctionId;

    private Long itemId;

    private Long buyerId;

    private Long sellerId;

    /** 成交价（分） */
    private Integer dealPrice;

    private String status;

    private String tradeType;

    private String tradePlace;

    private LocalDateTime completedAt;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
