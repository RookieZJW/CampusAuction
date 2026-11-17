package com.campus.auction.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 登录返回
 */
@Data
@Builder
public class LoginVO {

    private String token;

    private Long userId;

    private String nickname;

    private String avatar;

    /** 手机号（未绑定为空串），前端据此判断是否需要引导补全 */
    private String phone;

    /** 虚拟余额（分） */
    private Integer balance;

    /** 信用分 */
    private Integer creditScore;

    /** 是否新注册用户（前端可展示欢迎语） */
    private Boolean isNew;
}
