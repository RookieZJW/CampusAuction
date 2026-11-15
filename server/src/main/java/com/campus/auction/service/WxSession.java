package com.campus.auction.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * code2session 返回结构。
 * 微信成功响应为 {"openid":"...","session_key":"...","unionid":"..."}（无 errcode）；
 * 失败响应为 {"errcode":40029,"errmsg":"invalid code",...}（无 openid）。
 * 因此「有没有 openid」才是成功与否的判据，errcode 只作辅助。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WxSession {

    private String openid;

    /** 微信返回下划线字段名，需显式映射；本项目不下发前端，仅留作后续扩展 */
    @JsonProperty("session_key")
    private String sessionKey;

    /** 微信错误码：仅失败响应中出现，成功时为 null */
    private Integer errcode;

    private String errmsg;
}
