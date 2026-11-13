package com.campus.auction.vo;

import lombok.Builder;
import lombok.Data;

/** 举报记录（管理端） */
@Data
@Builder
public class ReportVO {

    private Long id;

    private String targetType;

    private Long targetId;

    private String reason;

    private String status;

    private String reporter;

    /** 被举报用户昵称（ITEM 举报=拍品卖家；USER 举报=该用户） */
    private String targetUser;

    /** 拍品标题（targetType=ITEM 时） */
    private String itemTitle;

    private String handleResult;

    private String createTime;
}
