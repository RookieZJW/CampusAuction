package com.campus.auction.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 支付状态视图：查询支付结果、模拟支付成功、关闭支付单、回调处理共用。
 */
@Data
@Builder
public class PayStatusVO {

    private Long orderId;

    private String orderNo;

    /** 商品描述：重复进入收银台时成功凭证页直接展示 */
    private String title;

    /** UNPAID / PAYING / PAID / CLOSED */
    private String payStatus;

    private String payNo;

    private String provider;

    /** 支付金额（分） */
    private Integer amount;

    /** 第三方交易号 */
    private String transactionId;

    /** 支付成功时间 yyyy-MM-dd HH:mm:ss */
    private String paidAt;
}
