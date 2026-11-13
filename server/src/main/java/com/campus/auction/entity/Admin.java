package com.campus.auction.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_admin")
public class Admin {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    /** SHA-256 哈希，不存明文 */
    private String passwordHash;

    /** 0 超级管理员 1 普通管理员 */
    private Integer role;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
