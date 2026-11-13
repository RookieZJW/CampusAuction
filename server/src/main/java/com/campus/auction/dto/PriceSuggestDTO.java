package com.campus.auction.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** AI 定价建议入参（发布页填写过程中随时可调） */
@Data
public class PriceSuggestDTO {

    @NotBlank(message = "标题不能为空")
    @Size(max = 100, message = "标题最长 100 字")
    private String title;

    @NotNull(message = "分类不能为空")
    private Long categoryId;

    @NotBlank(message = "成色不能为空")
    private String condition;

    @Size(max = 2000, message = "描述最长 2000 字")
    private String description;
}
