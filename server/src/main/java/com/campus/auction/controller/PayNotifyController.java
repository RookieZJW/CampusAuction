package com.campus.auction.controller;

import com.campus.auction.common.exception.BusinessException;
import com.campus.auction.payment.PaymentNotify;
import com.campus.auction.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 统一支付回调入口（WebConfig 白名单，无登录态，合法性由网关验签保证）。
 *
 * <p>微信支付 v3 要求 HTTP 200 + {@code {"code":"SUCCESS"}} 应答，故本接口
 * 不套项目统一 {@code Result} 包装；失败返回 {@code {"code":"FAIL"}}。
 *
 * <p>模拟渠道：/api/v1/pay/notify/mock；真实渠道：/api/v1/pay/notify/wxpay。
 */
@Slf4j
@Tag(name = "支付回调")
@RestController
@RequestMapping("/api/v1/pay")
@RequiredArgsConstructor
public class PayNotifyController {

    private final PaymentService paymentService;

    @Operation(summary = "支付结果回调", description = "验签 + 幂等处理；微信支付 v3 应答格式")
    @PostMapping("/notify/{provider}")
    public Map<String, String> notify(@PathVariable String provider,
                                      @RequestBody(required = false) Map<String, Object> body,
                                      @RequestHeader(value = "Wechatpay-Signature", required = false) String signature) {
        try {
            Map<String, Object> b = body == null ? Map.of() : body;
            String payNo = str(b.get("payNo") != null ? b.get("payNo") : b.get("out_trade_no"));
            String transactionId = str(
                    b.get("transactionId") != null ? b.get("transactionId") : b.get("transaction_id"));
            PaymentNotify notify = PaymentNotify.builder()
                    .provider(provider)
                    .payNo(payNo)
                    .transactionId(transactionId)
                    .rawBody(b.toString())
                    .signature(signature)
                    .build();
            paymentService.handleNotify(notify);
            return Map.of("code", "SUCCESS", "message", "成功");
        } catch (BusinessException e) {
            log.warn("支付回调处理失败：{}", e.getMessage());
            return Map.of("code", "FAIL", "message", e.getMessage());
        } catch (Exception e) {
            log.error("支付回调异常", e);
            return Map.of("code", "FAIL", "message", "系统异常");
        }
    }

    private String str(Object v) {
        return v == null ? null : v.toString();
    }
}
