package com.campus.auction.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 一步登录（新用户）：微信 code + 手机号授权 code + 昵称。
 * 头像由前端在拿到 token 后单独上传（chooseAvatar 只给临时路径，需鉴权后持久化）。
 */
@Data
public class RegisterDTO {

    /** wx.login 返回的 code，用于换 openid */
    @NotBlank(message = "微信登录凭证不能为空")
    private String wxCode;

    /** getPhoneNumber 回调的 code，用于换手机号 */
    @NotBlank(message = "手机号授权 code 不能为空")
    private String phoneCode;

    /** 用户在昵称输入框填写的昵称 */
    @NotBlank(message = "请填写昵称")
    @Size(max = 32, message = "昵称最多 32 字")
    private String nickname;
}
