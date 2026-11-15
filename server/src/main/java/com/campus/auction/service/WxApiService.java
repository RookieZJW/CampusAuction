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
     * 用 wx.login 的 code 换 openid。
     * 网络/解析异常统一转 3008，前端提示「微信登录失败」而非「系统繁忙」，便于排查。
     */
    public String code2Openid(String code) {
        if (mock) {
            // 模拟 openid：同一 code 稳定映射，方便联调
            log.warn("[mock 模式] 未调用微信 code2session，返回模拟 openid");
            return "mock-openid-" + Integer.toHexString(code.hashCode());
        }

        WxSession session;
        try {
            session = restTemplate.getForObject(JS_CODE_2_SESSION_URL, WxSession.class,
                    appid, secret, code);
        } catch (org.springframework.web.client.RestClientException e) {
            log.error("code2session 网络异常：{}", e.getMessage());
            throw new BusinessException(ResultCode.WX_LOGIN_FAILED);
        }
        // 微信成功响应没有 errcode 字段，只有 openid/session_key；
        // 失败响应才有 errcode。故以 openid 是否存在作为主判据，避免把成功当成失败。
        boolean success = session != null
                && session.getOpenid() != null && !session.getOpenid().isBlank()
                && (session.getErrcode() == null || session.getErrcode() == 0);
        if (!success) {
            log.error("code2session 失败：{}", session);
            throw new BusinessException(ResultCode.WX_LOGIN_FAILED);
        }
        log.info("code2session 成功：openid={}", maskOpenid(session.getOpenid()));
        return session.getOpenid();
    }

    /** 日志脱敏：只保留首尾各 4 位，避免 openid 明文落盘 */
    private String maskOpenid(String openid) {
        if (openid.length() <= 8) {
            return "****";
        }
        return openid.substring(0, 4) + "****" + openid.substring(openid.length() - 4);
    }
}
