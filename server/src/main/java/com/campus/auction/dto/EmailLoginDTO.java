package com.campus.auction.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 邮箱登录第二步：提交邮箱 + 验证码 + wx.login 的 code（用于拿当前 openid） */
@Data
public class EmailLoginDTO {

    @NotBlank(message = "邮箱不能为空")
    private String email;

    @NotBlank(message = "验证码不能为空")
    private String code;

    /** 当前微信 wx.login 返回的 code，用于账号打通时兜底注册 */
    @NotBlank(message = "微信登录凭证不能为空")
    private String wxCode;
}
