package com.campus.auction.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 发布拍品入参。金额单位：分
 */
@Data
public class PublishItemDTO {

    @NotBlank(message = "标题不能为空")
    @Size(max = 100, message = "标题最多 100 字")
    private String title;

    @NotNull(message = "分类不能为空")
    private Long categoryId;

    @Size(max = 2000, message = "描述最多 2000 字")
    private String description;

    /** 图片 URL 列表，0-3 张 */
    @Size(max = 3, message = "最多上传 3 张图片")
    private List<String> images;

    @NotBlank(message = "成色不能为空")
    private String condition;

    @NotNull(message = "起拍价不能为空")
    @Min(value = 100, message = "起拍价最低 1 元")
    @Max(value = 10000000, message = "起拍价过高")
    private Integer startPrice;

    @NotNull(message = "加价幅度不能为空")
    @Min(value = 10, message = "加价幅度最低 0.1 元")
    private Integer increment;

    /** 拍卖时长（小时），审核通过后起算 */
    @NotNull(message = "拍卖时长不能为空")
    @Min(value = 1, message = "时长至少 1 小时")
    @Max(value = 24 * 15, message = "时长最长 15 天")
    private Integer durationHours;
}
