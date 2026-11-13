package com.campus.auction.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BidRecordVO {

    private String nickname;

    private Integer price;

    private String bidTime;

    private String status;
}
