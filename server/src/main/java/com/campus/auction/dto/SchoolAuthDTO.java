package com.campus.auction.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 学校认证入参 */
@Data
public class SchoolAuthDTO {

    @NotNull(message = "请选择学校")
    private Long schoolId;
}
