package com.campus.auction.vo;

import lombok.Builder;
import lombok.Data;

/** 我的收藏列表项（拍场卡片信息） */
@Data
@Builder
public class FavoriteVO {

    private Long auctionId;

    private String title;

    private String coverImage;

    private String categoryName;

    private Integer currentPrice;

    private Integer bidCount;

    private String status;

    /** 剩余毫秒（ONGOING 时），前端倒计时用；非进行中为 null */
    private Long remainMs;
}
