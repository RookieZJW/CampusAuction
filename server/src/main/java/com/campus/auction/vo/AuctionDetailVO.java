package com.campus.auction.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 拍卖详情
 */
@Data
@Builder
public class AuctionDetailVO {

    private Long id;

    private Long itemId;

    private String title;

    private String description;

    private List<String> images;

    private String categoryName;

    /** 拍品所属学校（发布时确定，不随卖家后续改学校变化） */
    private Long schoolId;

    /** 学校简称，如「河南理工」 */
    private String schoolName;

    private String condition;

    private Integer startPrice;

    private Integer increment;

    private Integer deposit;

    private Integer currentPrice;

    private Integer bidCount;

    private String status;

    private String startTime;

    private String endTime;

    private String sellerNickname;

    private String sellerAvatar;

    /** 出价记录（倒序，最新在前） */
    private List<BidRecordVO> bidRecords;

    /** 当前用户是否已出过价（W3 竞价上线后启用） */
    private Boolean myLeading;
}
