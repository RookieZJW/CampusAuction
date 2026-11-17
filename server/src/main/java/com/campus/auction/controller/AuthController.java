package com.campus.auction.controller;

import com.campus.auction.common.Result;
import com.campus.auction.dto.LoginVO;
import com.campus.auction.dto.RegisterDTO;
import com.campus.auction.dto.WxLoginDTO;
import com.campus.auction.interceptor.AuthInterceptor;
import com.campus.auction.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口：老用户微信登录 + 新用户一步登录
 */
@Tag(name = "认证")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "微信登录", description = "老用户静默登录：wx.login 的 code 换 openid，签发 JWT")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody WxLoginDTO dto) {
        return Result.ok(authService.login(dto.getCode()));
    }

    @Operation(summary = "一步登录（新用户）", description = "微信 code + 手机号 code + 昵称，建号并写入资料")
    @PostMapping("/register")
    public Result<LoginVO> register(@Valid @RequestBody RegisterDTO dto) {
        return Result.ok(authService.register(dto.getWxCode(), dto.getPhoneCode(), dto.getNickname()));
    }

    /**
     * 联调自检接口：校验当前 token 是否有效，返回 openid
     */
    @Operation(summary = "登录态自检")
    @GetMapping("/check")
    public Result<String> check(@RequestAttribute(AuthInterceptor.ATTR_OPENID) String openid) {
        return Result.ok(openid);
    }
}
