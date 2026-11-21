package com.campus.auction.payment.impl;

import com.campus.auction.common.ResultCode;
import com.campus.auction.common.exception.BusinessException;
import com.campus.auction.payment.PaymentContext;
import com.campus.auction.payment.PaymentNotify;
import com.campus.auction.payment.PaymentProvider;
import com.campus.auction.vo.PayPrepareVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 微信支付网关骨架（预留，默认不启用）。
 *
 * <p><b>为什么只有骨架：</b>本项目主体是个体工商户小程序，且拍卖为 C2C 多卖家模式。
 * 若「买家付款到平台商户号、平台再结算给卖家」，属于资金归集 + 二次清算（二清），
 * 个体工商户主体也不适合经营性网络拍卖类目。正式上线应走微信支付「电商收付通」/
 * 服务商模式：卖家作为二级商户进件，资金直达卖家侧，平台只发分账/解冻指令。
 *
 * <p>本类保留完整接入位，配置 payment.provider=wxpay 后即装配；
 * 未完成商户进件与证书配置时调用会给出明确提示，避免误以为已接通真实资金。
 */
@Component
@ConditionalOnProperty(prefix = "payment", name = "provider", havingValue = "wxpay")
public class WxPayProvider implements PaymentProvider {

    @Value("${payment.wxpay.mch-id:}")
    private String mchId;

    @Value("${payment.wxpay.app-id:}")
    private String appId;

    @Value("${payment.wxpay.api-v3-key:}")
    private String apiV3Key;

    @Value("${payment.wxpay.notify-url:}")
    private String notifyUrl;

    @Override
    public String code() {
        return "WXPAY";
    }

    @Override
    public String displayName() {
        return "微信支付";
    }

    @Override
    public boolean isMock() {
        return false;
    }

    @Override
    public PayPrepareVO prepare(PaymentContext ctx) {
        // TODO 真实接入步骤：
        // 1) 商户号 mchId 与小程序 appId 绑定，配置 APIv3 密钥与商户私钥/证书序列号；
        // 2) POST https://api.mch.weixin.qq.com/v3/pay/transactions/jsapi
        //    body: appid / mchid / description / out_trade_no / notify_url(公网 HTTPS)
        //          / amount{total,currency:CNY} / payer{openid}
        //    请求头 Authorization: WECHATPAY2-SHA256-RSA2048 商户私钥签名；
        // 3) 取回 prepay_id，按 timeStamp/nonceStr/package=prepay_id=.../signType=RSA
        //    二次签名得到 paySign，填入 PayPrepareVO 交给前端 wx.requestPayment；
        // 4) 平台侧须以「电商收付通」进件二级商户，避免二清。
        throw new BusinessException(ResultCode.PAY_PROVIDER_UNAVAILABLE,
                "微信支付网关需先完成商户进件、APIv3 证书与电商收付通配置；演示请切回 payment.provider=mock");
    }

    @Override
    public boolean verifyNotify(PaymentNotify notify) {
        // TODO 真实接入步骤：
        // 1) 取 Wechatpay-Signature / Timestamp / Nonce / Serial 请求头；
        // 2) 用对应序列号的微信支付平台证书公钥验签（构造串：timestamp\nnonce\nbody\n）；
        // 3) 用 apiV3Key 解密 resource.ciphertext（AEAD_AES_256_GCM）
        //    得到 out_trade_no / transaction_id / trade_state；
        // 4) 校验商户号与金额，仅 SUCCESS 才放行，且必须做幂等（见 PaymentServiceImpl）。
        throw new BusinessException(ResultCode.PAY_PROVIDER_UNAVAILABLE,
                "微信支付回调验签尚未启用（需平台证书与 APIv3 密钥）");
    }
}
