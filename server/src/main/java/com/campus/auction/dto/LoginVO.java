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

    /** 学校ID（未认证为 null） */
    private Long schoolId;

    /** 学校简称（未认证为空串） */
    private String schoolName;

    /** 是否已完成学校认证 */
    private Boolean certified;

    /** 是否新注册用户（前端可展示欢迎语） */
    private Boolean isNew;
}
