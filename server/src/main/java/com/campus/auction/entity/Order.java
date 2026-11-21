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

    /** 支付状态（独立于交割状态，方案 B 可插拔支付网关） */
    public static final String PAY_UNPAID = "UNPAID";
    public static final String PAY_PAYING = "PAYING";
    public static final String PAY_PAID = "PAID";
    public static final String PAY_CLOSED = "CLOSED";

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

    /** 支付状态 UNPAID/PAYING/PAID/CLOSED */
    private String payStatus;

    /** 支付单号 PAY+时间戳+随机 */
    private String payNo;

    /** 支付网关标识 MOCK/WXPAY */
    private String payProvider;

    /** 本次支付金额（分） */
    private Integer payAmount;

    /** 第三方交易号（模拟支付为 MOCK 前缀） */
    private String transactionId;

    /** 支付成功时间 */
    private LocalDateTime paidAt;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
