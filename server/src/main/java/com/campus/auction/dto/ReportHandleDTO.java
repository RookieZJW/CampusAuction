package com.campus.auction.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 管理员处理举报入参：RESOLVED 已核实 / DISMISSED 已驳回 */
@Data
public class ReportHandleDTO {

    @NotBlank(message = "处理动作不能为空")
    private String action;

    @Size(max = 255, message = "处理说明最长 255 字")
    private String handleResult;
}
