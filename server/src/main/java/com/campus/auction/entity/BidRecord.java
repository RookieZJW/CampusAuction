package com.campus.auction.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_bid_record")
public class BidRecord {

    public static final String STATUS_LEADING = "LEADING";
    public static final String STATUS_OUTBID = "OUTBID";
    public static final String STATUS_WINNING = "WINNING";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long auctionId;

    private Long userId;

    private Integer price;

    private LocalDateTime bidTime;

    private String status;
}
