package com.campus.auction.service;

import com.campus.auction.dto.LoginVO;

public interface AuthService {

    /**
     * 微信登录：code 换 openid，首次登录自动注册（赠虚拟余额），
     * 签发 JWT
     */
    LoginVO login(String code);
}
