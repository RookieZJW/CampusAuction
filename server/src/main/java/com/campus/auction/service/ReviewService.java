package com.campus.auction.service;

import com.campus.auction.dto.ReviewDTO;
import com.campus.auction.vo.ReviewVO;

import java.util.List;

/** 订单互评 */
public interface ReviewService {

    /** 对某订单的对手方提交评价（订单须 COMPLETED，一单一人一次） */
    void submit(Long orderId, Long fromUserId, ReviewDTO dto);

    /** 我收到的评价 */
    List<ReviewVO> received(Long userId);

    /** 我发出的评价（订单页判断是否已评） */
    List<ReviewVO> sent(Long userId);
}
