package com.campus.auction.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 审核入参：驳回时必填理由
 */
@Data
public class AuditDTO {

    @Size(max = 255, message = "驳回理由最多 255 字")
    private String reason;
}
