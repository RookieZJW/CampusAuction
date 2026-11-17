package com.campus.auction.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学生用户表 t_user
 * 金额单位一律为“分”（int），禁用浮点
 */
@Data
@TableName("t_user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 微信 openid，唯一 */
    private String openid;

    private String nickname;

    private String avatar;

    /** 手机号登录凭证，唯一（账号打通用） */
    private String phone;

    /** 邮箱登录凭证，唯一（账号打通用） */
    private String email;

    /** 信用分：初始 100，低于 60 限制出价 */
    private Integer creditScore;

    /** 虚拟余额（分）：注册赠 100 元 = 10000 分 */
    private Integer balance;

    /** 0 正常 / 1 封禁 */
    @TableField("status")
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
