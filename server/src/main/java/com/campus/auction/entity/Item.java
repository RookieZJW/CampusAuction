package com.campus.auction.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 拍品（物品静态信息）
 * 状态流转：DRAFT → PENDING(待审) → ON_SALE / REJECTED → (可编辑后重新提交)
 */
@Data
@TableName("t_item")
public class Item {

    /** 物品状态常量 */
    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_ON_SALE = "ON_SALE";
    public static final String STATUS_REJECTED = "REJECTED";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sellerId;

    /** 发布时卖家所属学校，冗余免联表 */
    private Long schoolId;

    private Long categoryId;

    private String title;

    private String description;

    /** 图片 URL 数组（JSON 字符串），最多 3 张 */
    private String images;

    @TableField("`condition`")
    private String condition;

    private Integer aiSuggestMin;

    private Integer aiSuggestMax;

    private String status;

    private String rejectReason;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
