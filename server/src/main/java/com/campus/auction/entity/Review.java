package com.campus.auction.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 订单互评：一单一人只能评一次（uk_order_from），评价对象为订单对手方 */
@Data
@TableName("t_review")
public class Review {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private Long fromUser;

    private Long toUser;

    /** 1-5 星 */
    private Integer rating;

    private String content;

    /** 评价标签，逗号分隔（预留） */
    private String tags;

    private LocalDateTime createTime;
}
