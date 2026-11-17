package com.campus.auction.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 邮箱登录第一步：请求发送验证码 */
@Data
public class EmailCodeDTO {

    @NotBlank(message = "邮箱不能为空")
    private String email;
}
