package com.campus.auction.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 我发布的拍品（含其拍卖场次状态）
 */
@Data
@Builder
public class MyItemVO {

    private Long itemId;

    private Long auctionId;

    private String title;

    private List<String> images;

    /** PENDING待审 ON_SALE拍卖中 REJECTED已驳回 CANCELLED已取消 */
    private String itemStatus;

    /** 拍卖状态 ONGOING/FINISHED/FAILED/CANCELLED，可能为空（无场次） */
    private String auctionStatus;

    private String rejectReason;

    private Integer currentPrice;

    private Integer bidCount;

    private String endTime;
}
