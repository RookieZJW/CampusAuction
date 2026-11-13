package com.campus.auction.controller;

import com.campus.auction.common.Result;
import com.campus.auction.dto.LoginVO;
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
 * 认证接口
 */
@Tag(name = "认证")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "微信登录", description = "入参为 wx.login 返回的 code，登录成功返回 JWT")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody WxLoginDTO dto) {
        return Result.ok(authService.login(dto.getCode()));
    }

    /**
     * 联调自检接口：校验当前 token 是否有效，返回 openid
     * （供小程序端“登录态检查”与真机调试使用）
     */
    @Operation(summary = "登录态自检")
    @GetMapping("/check")
    public Result<String> check(@RequestAttribute(AuthInterceptor.ATTR_OPENID) String openid) {
        return Result.ok(openid);
    }
}
