package com.campus.auction.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 出价结果
 */
@Data
@Builder
public class BidVO {

    private Integer currentPrice;

    private Integer bidCount;

    private String endTime;

    /** 下一次出价的最低金额（分） */
    private Integer minNext;

    /** 出价后是否处于领先 */
    private Boolean isLeading;

    /** 是否触发了防狙击延时 */
    private Boolean extended;
}
