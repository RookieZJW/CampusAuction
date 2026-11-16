package com.campus.auction.service;

import com.campus.auction.common.ResultCode;
import com.campus.auction.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * 微信开放接口封装：code2session 登录、access_token、手机号快速验证。
 *
 * <p>code2session 文档：
 * https://developers.weixin.qq.com/miniprogram/dev/OpenApiDoc/user-login/code2session.html
 * <p>手机号快速验证文档：
 * https://developers.weixin.qq.com/miniprogram/dev/framework/open-ability/getPhoneNumber.html
 *
 * <p>wx.mock=true 时不调微信服务器，直接生成模拟 openid（离线跑通登录闭环）；
 * 但 access_token 与手机号接口在 mock 模式下明确拒绝，避免写入假数据。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WxApiService {

    private static final String JS_CODE_2_SESSION_URL =
            "https://api.weixin.qq.com/sns/jscode2session?appid={appid}&secret={secret}&js_code={code}&grant_type=authorization_code";
    private static final String ACCESS_TOKEN_URL =
            "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid={appid}&secret={secret}";
    private static final String GET_PHONE_NUMBER_URL =
            "https://api.weixin.qq.com/wxa/business/getuserphonenumber?access_token={token}";

    private final RestTemplate restTemplate;

    /** access_token 有效期 2 小时，提前 5 分钟刷新；synchronized 避免并发重复获取 */
    private volatile String cachedAccessToken;
    private volatile long accessTokenExpireAt;

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

    /**
     * 获取小程序全局 access_token（带缓存）。
     * 该凭证只在服务端使用，绝不下发前端；日志中也不打印。
     */
    public synchronized String getAccessToken() {
        if (mock) {
            throw new BusinessException(ResultCode.BIZ_ERROR,
                    "mock 模式不支持调用微信开放接口，请关闭 wx.mock 后重试");
        }
        if (cachedAccessToken != null && System.currentTimeMillis() < accessTokenExpireAt) {
            return cachedAccessToken;
        }
        JsonNode resp;
        try {
            resp = restTemplate.getForObject(ACCESS_TOKEN_URL, JsonNode.class, appid, secret);
        } catch (org.springframework.web.client.RestClientException e) {
            log.error("获取 access_token 网络异常：{}", e.getMessage());
            throw new BusinessException(ResultCode.BIZ_ERROR, "微信服务暂不可用，请稍后重试");
        }
        if (resp == null || resp.path("access_token").asText().isBlank()) {
            log.error("获取 access_token 失败：errcode={}",
                    resp == null ? "null" : resp.path("errcode").asInt());
            throw new BusinessException(ResultCode.BIZ_ERROR,
                    "微信凭证获取失败，请检查 AppID / AppSecret 配置");
        }
        cachedAccessToken = resp.path("access_token").asText();
        long expiresIn = resp.path("expires_in").asLong(7200);
        // 提前 5 分钟过期，避免边界时刻用失效凭证
        accessTokenExpireAt = System.currentTimeMillis() + Math.max(60, expiresIn - 300) * 1000;
        log.info("微信 access_token 已刷新，有效期 {} 秒", expiresIn);
        return cachedAccessToken;
    }

    /**
     * 用 getPhoneNumber 组件回调里的动态 code 换取手机号。
     * code 有效期 5 分钟且只能消费一次；此 code 与 wx.login 的 code 不通用。
     * 返回值不写入日志，避免手机号明文落盘。
     */
    public String getPhoneNumber(String code) {
        String token = getAccessToken();
        JsonNode resp;
        try {
            resp = restTemplate.postForObject(GET_PHONE_NUMBER_URL,
                    Map.of("code", code), JsonNode.class, token);
        } catch (org.springframework.web.client.RestClientException e) {
            log.error("换取手机号网络异常：{}", e.getMessage());
            throw new BusinessException(ResultCode.BIZ_ERROR, "微信服务暂不可用，请稍后重试");
        }
        if (resp == null || resp.path("errcode").asInt(0) != 0) {
            log.error("换取手机号失败：errcode={} errmsg={}",
                    resp == null ? "null" : resp.path("errcode").asInt(),
                    resp == null ? "" : resp.path("errmsg").asText());
            throw new BusinessException(ResultCode.BIZ_ERROR, "手机号获取失败，请重新授权");
        }
        String phone = resp.path("phone_info").path("purePhoneNumber").asText();
        if (phone.isBlank()) {
            throw new BusinessException(ResultCode.BIZ_ERROR, "未获取到手机号，请重新授权");
        }
        return phone;
    }

    /** 日志脱敏：只保留首尾各 4 位，避免 openid 明文落盘 */
    private String maskOpenid(String openid) {
        if (openid.length() <= 8) {
            return "****";
        }
        return openid.substring(0, 4) + "****" + openid.substring(openid.length() - 4);
    }
}
