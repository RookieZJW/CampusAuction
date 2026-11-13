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

    /** 虚拟余额（分） */
    private Integer balance;

    /** 信用分 */
    private Integer creditScore;

    /** 是否新注册用户（前端可展示欢迎语） */
    private Boolean isNew;
}
