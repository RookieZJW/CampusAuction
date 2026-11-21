package com.campus.auction.payment;

import com.campus.auction.vo.PayPrepareVO;

/**
 * 可插拔支付网关接口（方案 B）。
 *
 * <p>设计目标：把「资金链路」从订单履约状态机里解耦出来。当前默认装配
 * {@code MockPaymentProvider}（模拟支付，答辩可演示、零合规风险）；
 * 未来接入真实微信支付时只需新增一个实现并切换 payment.provider 配置，
 * 订单状态机、回调幂等、前端收银台均无需重写。
 *
 * <p>真实渠道接入要点见 {@code WxPayProvider} 中的 TODO 清单。
 */
public interface PaymentProvider {

    /** 网关标识：MOCK / WXPAY，落库到 t_order.pay_provider */
    String code();

    /** 收银台展示名 */
    String displayName();

    /** 是否模拟渠道：true 时前端展示自绘收银台，不调用 wx.requestPayment */
    boolean isMock();

    /**
     * 创建支付单，返回拉起收银台所需参数。
     * 真实渠道在这里调「统一下单」并生成 paySign；模拟渠道直接返回占位参数。
     */
    PayPrepareVO prepare(PaymentContext ctx);

    /**
     * 校验回调合法性（真实渠道为 APIv3 平台证书验签 + 报文解密）。
     * 模拟渠道无平台签名，仅校验支付单号非空。
     */
    boolean verifyNotify(PaymentNotify notify);
}
