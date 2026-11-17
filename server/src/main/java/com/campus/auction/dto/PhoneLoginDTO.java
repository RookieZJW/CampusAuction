package com.campus.auction.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 手机号登录：getPhoneNumber 动态 code + wx.login 的 code */
@Data
public class PhoneLoginDTO {

    /** getPhoneNumber 回调的 code（5 分钟一次性） */
    @NotBlank(message = "手机号授权 code 不能为空")
    private String phoneCode;

    /** 当前微信 wx.login 返回的 code，用于账号打通时兜底注册 */
    @NotBlank(message = "微信登录凭证不能为空")
    private String wxCode;
}
