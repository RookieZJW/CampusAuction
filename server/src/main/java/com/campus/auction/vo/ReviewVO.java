package com.campus.auction.vo;

import lombok.Builder;
import lombok.Data;

/** 互评记录（收到/发出视角） */
@Data
@Builder
public class ReviewVO {

    private Long id;

    private Long orderId;

    private Integer rating;

    private String content;

    /** 对手方昵称（收到视角=评价人；发出视角=被评人） */
    private String counterparty;

    /** 拍品标题 */
    private String itemTitle;

    private String createTime;
}
