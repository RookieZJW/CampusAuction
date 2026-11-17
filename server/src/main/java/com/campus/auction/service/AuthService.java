package com.campus.auction.service;

import com.campus.auction.dto.LoginVO;

public interface AuthService {

    /** 微信登录：code 换 openid，首次登录自动注册，签发 JWT */
    LoginVO login(String code);

    /** 手机号登录：getPhoneNumber code 换手机号 → 查/建账号 → 签发 JWT */
    LoginVO loginByPhone(String phoneCode, String wxCode);

    /** 邮箱登录：校验验证码 → 查/建账号 → 签发 JWT */
    LoginVO loginByEmail(String email, String code, String wxCode);
}
