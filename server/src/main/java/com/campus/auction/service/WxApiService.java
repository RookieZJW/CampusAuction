package com.campus.auction.service;

import com.campus.auction.common.ResultCode;
import com.campus.auction.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * 微信 code2session 接口封装
 * 官方文档：https://developers.weixin.qq.com/miniprogram/dev/OpenApiDoc/user-login/code2session.html
 *
 * wx.mock=true 时不调微信服务器，直接生成模拟 openid——
 * 用于没有 AppSecret 或离线开发时跑通登录闭环
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WxApiService {

    private static final String JS_CODE_2_SESSION_URL =
            "https://api.weixin.qq.com/sns/jscode2session?appid={appid}&secret={secret}&js_code={code}&grant_type=authorization_code";

    private final RestTemplate restTemplate;

    @Value("${wx.appid}")
    private String appid;

    @Value("${wx.secret}")
    private String secret;

    @Value("${wx.mock}")
    private boolean mock;

    /**
     * 用 wx.login 的 code 换 openid
     */
    public String code2Openid(String code) {
        if (mock) {
            // 模拟 openid：同一 code 稳定映射，方便联调
            log.warn("[mock 模式] 未调用微信 code2session，返回模拟 openid");
            return "mock-openid-" + Integer.toHexString(code.hashCode());
        }

        WxSession session = restTemplate.getForObject(JS_CODE_2_SESSION_URL, WxSession.class,
                appid, secret, code);
        if (session == null || session.getErrcode() == null || session.getErrcode() != 0
                || session.getOpenid() == null) {
            log.error("code2session 失败：{}", session);
            throw new BusinessException(ResultCode.WX_LOGIN_FAILED);
        }
        return session.getOpenid();
    }
}
