package com.campus.auction.payment.impl;

import com.campus.auction.payment.PaymentContext;
import com.campus.auction.payment.PaymentNotify;
import com.campus.auction.payment.PaymentProvider;
import com.campus.auction.util.TimeUtil;
import com.campus.auction.vo.PayPrepareVO;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 模拟支付网关（默认启用，答辩演示用）。
 *
 * <p>不产生任何真实资金变动：不调用微信支付接口、不改变虚拟余额、
 * 不触碰货款清算。它只负责生成支付单参数与「已支付」状态，
 * 让订单资金链路在演示环境中完整可见且零合规风险。
 *
 * <p>切换方式：application.yml 中 payment.provider=wxpay 即换用 WxPayProvider。
 */
@Component
@ConditionalOnProperty(prefix = "payment", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockPaymentProvider implements PaymentProvider {

    private static final String NOTIFY_PATH = "/api/v1/pay/notify/mock";

    @Override
    public String code() {
        return "MOCK";
    }

    @Override
    public String displayName() {
        return "微信支付（模拟）";
    }

    @Override
    public boolean isMock() {
        return true;
    }

    @Override
    public PayPrepareVO prepare(PaymentContext ctx) {
        Integer amount = ctx.getAmount() == null ? 0 : ctx.getAmount();
        return PayPrepareVO.builder()
                .payNo(ctx.getPayNo())
                .orderNo(ctx.getOrderNo())
                .orderId(ctx.getOrderId())
                .provider(code())
                .providerName(displayName())
                .mock(true)
                .amount(amount)
                .amountYuan(String.format("%.2f", amount / 100.0))
                .title(ctx.getTitle())
                .prepayId("MOCK_PREPAY_" + ctx.getPayNo())
                .notifyUrl(NOTIFY_PATH)
                .expireTime(TimeUtil.str(LocalDateTime.now().plusMinutes(15)))
                .payStatus("PAYING")
                .build();
    }

    @Override
    public boolean verifyNotify(PaymentNotify notify) {
        // 模拟渠道没有平台签名，仅校验支付单号非空；
        // 真实渠道在这里替换为 Wechatpay-Signature 验签 + APIv3 报文解密。
        return notify != null && notify.getPayNo() != null && !notify.getPayNo().isBlank();
    }
}
