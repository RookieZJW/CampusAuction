package com.campus.auction.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 大厅列表项（前端已按此结构对接）
 */
@Data
@Builder
public class AuctionListVO {

    private Long id;

    private String title;

    private List<String> images;

    private String categoryName;

    private String condition;

    private Integer currentPrice;

    private Integer bidCount;

    private String endTime;
}
