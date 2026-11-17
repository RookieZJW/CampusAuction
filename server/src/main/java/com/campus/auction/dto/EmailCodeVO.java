package com.campus.auction.dto;

import lombok.Builder;
import lombok.Data;

/** 邮箱验证码发送结果 */
@Data
@Builder
public class EmailCodeVO {

    /** 验证码有效期（秒） */
    private Integer expireSeconds;

    /**
     * 开发模式下的固定验证码，供答辩演示直接填写；
     * 生产模式为空串（真实发信，验证码只进用户邮箱）。
     */
    private String devCode;

    /** 是否开发模式（前端可据此展示"演示验证码"提示） */
    private Boolean devMode;
}
