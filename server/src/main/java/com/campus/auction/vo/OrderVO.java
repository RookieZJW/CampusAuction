package com.campus.auction.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 订单视图（买方/卖方通用）
 */
@Data
@Builder
public class OrderVO {

    private Long orderId;

    private String orderNo;

    /** 当前用户角色：BUYER / SELLER */
    private String role;

    private Long itemId;

    private String title;

    private String coverImage;

    private Integer dealPrice;

    /** PENDING_TRADING待交割 COMPLETED已完成 CANCELLED已取消(违约) */
    private String status;

    private String tradePlace;

    /** 支付状态 UNPAID/PAYING/PAID/CLOSED */
    private String payStatus;

    private String payNo;

    private String payProvider;

    private Integer payAmount;

    private String transactionId;

    /** 支付成功时间 yyyy-MM-dd HH:mm:ss */
    private String paidAt;

    /** 对手方昵称 */
    private String counterparty;

    private String createTime;
}
