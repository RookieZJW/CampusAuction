package com.campus.auction.controller;

import com.campus.auction.common.Result;
import com.campus.auction.dto.EmailCodeDTO;
import com.campus.auction.dto.EmailCodeVO;
import com.campus.auction.dto.EmailLoginDTO;
import com.campus.auction.dto.LoginVO;
import com.campus.auction.dto.PhoneLoginDTO;
import com.campus.auction.dto.WxLoginDTO;
import com.campus.auction.interceptor.AuthInterceptor;
import com.campus.auction.service.AuthService;
import com.campus.auction.service.EmailCodeService;
import com.campus.auction.service.impl.EmailCodeServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口：微信 / 手机号 / 邮箱 三种登录方式
 */
@Tag(name = "认证")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final EmailCodeService emailCodeService;

    @Value("${auth.email.code-ttl-minutes:5}")
    private int codeTtlMinutes;

    @Operation(summary = "微信登录", description = "入参为 wx.login 返回的 code，登录成功返回 JWT")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody WxLoginDTO dto) {
        return Result.ok(authService.login(dto.getCode()));
    }

    @Operation(summary = "手机号登录", description = "getPhoneNumber 的 code + wx.login 的 code")
    @PostMapping("/login/phone")
    public Result<LoginVO> loginByPhone(@Valid @RequestBody PhoneLoginDTO dto) {
        return Result.ok(authService.loginByPhone(dto.getPhoneCode(), dto.getWxCode()));
    }

    @Operation(summary = "发送邮箱验证码", description = "开发模式固定码并回显，生产模式真实发信")
    @PostMapping("/email/code")
    public Result<EmailCodeVO> sendEmailCode(@Valid @RequestBody EmailCodeDTO dto) {
        emailCodeService.sendCode(dto.getEmail());
        String devCode = "";
        boolean devMode = false;
        if (emailCodeService instanceof EmailCodeServiceImpl) {
            devCode = ((EmailCodeServiceImpl) emailCodeService).devCodeForDemo();
            devMode = !devCode.isEmpty();
        }
        return Result.ok(EmailCodeVO.builder()
                .expireSeconds(codeTtlMinutes * 60)
                .devCode(devCode)
                .devMode(devMode)
                .build());
    }

    @Operation(summary = "邮箱登录", description = "邮箱 + 验证码 + wx.login 的 code")
    @PostMapping("/login/email")
    public Result<LoginVO> loginByEmail(@Valid @RequestBody EmailLoginDTO dto) {
        return Result.ok(authService.loginByEmail(dto.getEmail(), dto.getCode(), dto.getWxCode()));
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
