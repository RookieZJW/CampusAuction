package com.campus.auction.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 学生证认证申请入参：学校 + 真实姓名 + 学号 + 学生证照片
 */
@Data
public class SchoolAuthSubmitDTO {

    @NotNull(message = "请选择学校")
    private Long schoolId;

    @NotBlank(message = "请填写真实姓名")
    @Size(max = 32, message = "姓名最长 32 字")
    private String realName;

    @NotBlank(message = "请填写学号")
    @Size(max = 32, message = "学号最长 32 字")
    private String studentNo;

    @NotBlank(message = "请上传学生证照片")
    @Size(max = 512, message = "学生证照片地址过长")
    private String studentCard;
}
