package com.campus.auction.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 用户提交举报入参 */
@Data
public class ReportDTO {

    /** ITEM 拍品 / USER 用户 */
    @NotBlank(message = "举报对象类型不能为空")
    private String targetType;

    private Long targetId;

    @NotBlank(message = "举报理由不能为空")
    @Size(max = 255, message = "理由最长 255 字")
    private String reason;
}
