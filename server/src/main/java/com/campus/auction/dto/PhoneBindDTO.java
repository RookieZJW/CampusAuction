package com.campus.auction.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 绑定手机号入参：来自 &lt;button open-type="getPhoneNumber"&gt; 回调的动态令牌。
 *
 * <p>该 code 有效期 5 分钟、只能消费一次，且与 wx.login 的 code 不通用。
 * 服务端用它调用 phonenumber.getPhoneNumber 换取手机号，前端拿不到明文。
 */
@Data
public class PhoneBindDTO {

    @NotBlank(message = "手机号授权 code 不能为空")
    private String code;
}
