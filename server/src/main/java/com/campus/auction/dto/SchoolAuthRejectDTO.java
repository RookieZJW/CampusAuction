package com.campus.auction.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 管理员驳回学生证认证申请入参 */
@Data
public class SchoolAuthRejectDTO {

    @NotBlank(message = "请填写驳回理由")
    @Size(max = 255, message = "驳回理由最长 255 字")
    private String reason;
}
