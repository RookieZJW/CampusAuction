package com.campus.auction.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 我的出价列表项
 */
@Data
@Builder
public class MyBidVO {

    private Long auctionId;

    private Long itemId;

    private String title;

    private String coverImage;

    /** 我的出价金额（分） */
    private Integer myPrice;

    /** 我的出价状态：LEADING领先 OUTBID被超越 WINNING中标 */
    private String myStatus;

    private Integer currentPrice;

    private Integer bidCount;

    private String endTime;

    private String auctionStatus;
}
