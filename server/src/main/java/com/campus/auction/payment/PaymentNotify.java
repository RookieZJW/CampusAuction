package com.campus.auction.payment;

import lombok.Builder;
import lombok.Data;

/**
 * 统一回调载荷。
 *
 * <p>模拟支付由 /orders/{id}/pay/confirm 内部构造并复用同一条回调处理链路，
 * 保证「回调验签 → 幂等 → CAS 状态流转」这条逻辑在演示时就是真实跑通的；
 * 真实微信支付回调则由 PayNotifyController 把原始报文/signature 填进来。
 */
@Data
@Builder
public class PaymentNotify {

    /** 渠道标识 MOCK / WXPAY，为空时按当前启用的网关处理 */
    private String provider;

    /** 商户支付单号 out_trade_no */
    private String payNo;

    /** 第三方交易号 transaction_id */
    private String transactionId;

    /** 原始回调报文（真实渠道验签/解密用） */
    private String rawBody;

    /** 平台签名（真实渠道验签用） */
    private String signature;
}
