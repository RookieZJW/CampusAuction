package com.campus.auction.service;

import com.campus.auction.vo.OrderVO;

import java.util.List;
import java.util.Map;

public interface OrderService {

    /** 我的订单（买入+卖出，含拍品与对手方信息） */
    List<OrderVO> myOrders(Long userId);

    /** 买方确认完成交割：订单 COMPLETED + 解冻中标者保证金 + 双方信用分+2 + 通知 */
    void completeTrade(Long orderId, Long userId);

    /** 买/卖双方约定交割地点 */
    void updateTradePlace(Long orderId, Long userId, String place);
}
