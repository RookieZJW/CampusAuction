package com.campus.auction.service;

import com.campus.auction.dto.LoginVO;

public interface AuthService {

    /** 微信登录（老用户静默登录）：code 换 openid，首次登录自动注册，签发 JWT */
    LoginVO login(String code);

    /** 一步登录（新用户）：微信 code + 手机号 code + 昵称，建号并写入资料 */
    LoginVO register(String wxCode, String phoneCode, String nickname);
}
