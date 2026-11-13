package com.campus.auction.vo;

import lombok.Builder;
import lombok.Data;

/** AI 定价建议结果。价格单位：分。source：AI 大模型 / RULE 规则降级 */
@Data
@Builder
public class PriceSuggestVO {

    private Integer minPrice;

    private Integer maxPrice;

    /** AI / RULE */
    private String source;

    private String reason;
}
