package com.campus.auction.service;

import lombok.Data;

/**
 * code2session 返回结构
 */
@Data
public class WxSession {

    private String openid;

    private String sessionKey;

    /** 微信错误码，0 为成功 */
    private Integer errcode;

    private String errmsg;
}
