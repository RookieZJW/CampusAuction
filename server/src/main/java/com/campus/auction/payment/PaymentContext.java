package com.campus.auction.payment;

import lombok.Builder;
import lombok.Data;

/**
 * 创建支付单的上下文：由订单信息组装，屏蔽网关实现与订单表的直接耦合。
 */
@Data
@Builder
public class PaymentContext {

    private Long orderId;

    private String orderNo;

    /** 支付单号（服务端生成，全局唯一） */
    private String payNo;

    /** 支付金额（分） */
    private Integer amount;

    /** 商品描述，用于收银台展示 */
    private String title;

    /** 回调地址（真实渠道必须为公网 HTTPS） */
    private String notifyUrl;
}
