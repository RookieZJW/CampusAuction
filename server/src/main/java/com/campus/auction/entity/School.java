package com.campus.auction.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学校字典 t_school（多校隔离）
 */
@Data
@TableName("t_school")
public class School {

    public static final int STATUS_ENABLED = 1;
    public static final int STATUS_DISABLED = 0;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 学校全称 */
    private String name;

    /** 简称，小程序展示用 */
    private String shortName;

    /** 1启用 0停用 */
    private Integer status;

    /** 排序，越小越靠前 */
    private Integer sort;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
