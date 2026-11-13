package com.campus.auction.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 审核列表项（管理员端）
 */
@Data
@Builder
public class AuditItemVO {

    private Long itemId;

    private Long sellerId;

    private String sellerNickname;

    private String title;

    private String description;

    private List<String> images;

    private String categoryName;

    private String condition;

    private Integer startPrice;

    private Integer increment;

    private Integer deposit;

    private Integer durationHours;

    private String submitTime;
}
