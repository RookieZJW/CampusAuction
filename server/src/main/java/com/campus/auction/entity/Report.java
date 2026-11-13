package com.campus.auction.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 举报：管理员核实后可对被举报用户扣信用分 */
@Data
@TableName("t_report")
public class Report {

    public static final String TARGET_ITEM = "ITEM";
    public static final String TARGET_USER = "USER";

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RESOLVED = "RESOLVED";
    public static final String STATUS_DISMISSED = "DISMISSED";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long reporterId;

    /** ITEM 拍品 / USER 用户 */
    private String targetType;

    private Long targetId;

    private String reason;

    /** PENDING 待处理 / RESOLVED 已核实 / DISMISSED 已驳回 */
    private String status;

    private Long handlerId;

    private String handleResult;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
