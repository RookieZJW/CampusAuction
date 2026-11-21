package com.campus.auction.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 发起支付返回：小程序据此渲染收银台。
 */
@Data
@Builder
public class PayPrepareVO {

    private String payNo;

    private String orderNo;

    private Long orderId;

    /** 网关标识 MOCK / WXPAY */
    private String provider;

    /** 收银台展示名 */
    private String providerName;

    /** true = 模拟渠道，前端不调 wx.requestPayment，展示自绘收银台 */
    private Boolean mock;

    /** 支付金额（分） */
    private Integer amount;

    /** 支付金额（元，保留两位） */
    private String amountYuan;

    /** 商品描述 */
    private String title;

    /** 预支付标识：真实渠道为 prepay_id，模拟渠道为占位串 */
    private String prepayId;

    private String notifyUrl;

    /** 支付单过期时间 yyyy-MM-dd HH:mm:ss */
    private String expireTime;

    /** 发起后的支付状态，固定 PAYING */
    private String payStatus;
}
