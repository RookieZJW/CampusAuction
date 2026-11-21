package com.campus.auction.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学校认证申请 t_school_auth（学生证 + 管理员审核）
 */
@Data
@TableName("t_school_auth")
public class SchoolAuth {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long schoolId;

    /** 真实姓名 */
    private String realName;

    /** 学号 */
    private String studentNo;

    /** 学生证照片 URL */
    private String studentCard;

    private String status;

    private String rejectReason;

    private Long reviewerId;

    private LocalDateTime reviewTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
