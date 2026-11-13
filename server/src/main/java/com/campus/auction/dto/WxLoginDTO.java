package com.campus.auction.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 微信登录入参：wx.login 返回的 code
 */
@Data
public class WxLoginDTO {

    @NotBlank(message = "code 不能为空")
    private String code;
}
