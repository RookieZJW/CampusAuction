package com.campus.auction.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.auction.common.ResultCode;
import com.campus.auction.common.exception.BusinessException;
import com.campus.auction.dto.ReviewDTO;
import com.campus.auction.entity.Item;
import com.campus.auction.entity.Notification;
import com.campus.auction.entity.Order;
import com.campus.auction.entity.Review;
import com.campus.auction.entity.User;
import com.campus.auction.mapper.ItemMapper;
import com.campus.auction.mapper.NotificationMapper;
import com.campus.auction.mapper.OrderMapper;
import com.campus.auction.mapper.ReviewMapper;
import com.campus.auction.mapper.UserMapper;
import com.campus.auction.service.ReviewService;
import com.campus.auction.vo.ReviewVO;import com.campus.auction.util.TimeUtil;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewMapper reviewMapper;
    private final OrderMapper orderMapper;
    private final ItemMapper itemMapper;
    private final UserMapper userMapper;
    private final NotificationMapper notificationMapper;

    @Override
    public void submit(Long orderId, Long fromUserId, ReviewDTO dto) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "订单不存在");
        }
        boolean isBuyer = order.getBuyerId().equals(fromUserId);
        boolean isSeller = order.getSellerId().equals(fromUserId);
        if (!isBuyer && !isSeller) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能评价自己参与的订单");
        }
        if (!Order.STATUS_COMPLETED.equals(order.getStatus())) {
            throw new BusinessException(ResultCode.BIZ_ERROR, "订单完成后才能评价");
        }

        Long toUser = isBuyer ? order.getSellerId() : order.getBuyerId();
        // 数据库 uk_order_from 兜底 + 提前查询给友好提示
        Long existed = reviewMapper.selectCount(
                new LambdaQueryWrapper<Review>()
                        .eq(Review::getOrderId, orderId)
                        .eq(Review::getFromUser, fromUserId));
        if (existed > 0) {
            throw new BusinessException(ResultCode.BIZ_ERROR, "该订单你已评价过");
        }

        Review review = new Review();
        review.setOrderId(orderId);
        review.setFromUser(fromUserId);
        review.setToUser(toUser);
        review.setRating(dto.getRating());
        review.setContent(dto.getContent());
        review.setCreateTime(LocalDateTime.now());
        reviewMapper.insert(review);

        // 通知被评价方
        Item item = itemMapper.selectById(order.getItemId());
        String title = item == null ? "拍品" : item.getTitle();
        Notification n = new Notification();
        n.setUserId(toUser);
        n.setType("REVIEW");
        n.setTitle("收到新评价");
        n.setContent("「" + title + "」的交易对方给了你 " + dto.getRating() + " 星评价。");
        n.setRefId(orderId);
        n.setIsRead(0);
        n.setCreateTime(LocalDateTime.now());
        notificationMapper.insert(n);
    }

    @Override
    public List<ReviewVO> received(Long userId) {
        List<Review> reviews = reviewMapper.selectList(
                new LambdaQueryWrapper<Review>()
                        .eq(Review::getToUser, userId)
                        .orderByDesc(Review::getCreateTime)
                        .last("LIMIT 50"));
        return toVOs(reviews, true);
    }

    @Override
    public List<ReviewVO> sent(Long userId) {
        List<Review> reviews = reviewMapper.selectList(
                new LambdaQueryWrapper<Review>()
                        .eq(Review::getFromUser, userId)
                        .orderByDesc(Review::getCreateTime)
                        .last("LIMIT 50"));
        return toVOs(reviews, false);
    }

    private List<ReviewVO> toVOs(List<Review> reviews, boolean received) {
        if (reviews.isEmpty()) {
            return Collections.emptyList();
        }
        // 收到视角：对手方是评价人；发出视角：对手方是被评人
        List<Long> counterpartyIds = reviews.stream()
                .map(r -> received ? r.getFromUser() : r.getToUser()).distinct().toList();
        Map<Long, String> nicknameMap = counterpartyIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(counterpartyIds).stream()
                        .collect(Collectors.toMap(User::getId,
                                u -> u.getNickname() == null ? "微信用户" : u.getNickname()));
        Map<Long, String> titleMap = itemTitles(reviews);

        return reviews.stream().map(r -> ReviewVO.builder()
                .id(r.getId())
                .orderId(r.getOrderId())
                .rating(r.getRating())
                .content(r.getContent())
                .counterparty(nicknameMap.getOrDefault(
                        received ? r.getFromUser() : r.getToUser(), "微信用户"))
                .itemTitle(titleMap.getOrDefault(r.getOrderId(), "已删除拍品"))
                .createTime(TimeUtil.str(r.getCreateTime()))
                .build()).toList();
    }

    /** 订单号 → 拍品标题 */
    private Map<Long, String> itemTitles(List<Review> reviews) {
        List<Long> orderIds = reviews.stream().map(Review::getOrderId).distinct().toList();
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        List<Order> orders = orderMapper.selectBatchIds(orderIds);
        List<Long> itemIds = orders.stream().map(Order::getItemId).distinct().toList();
        Map<Long, String> itemTitle = itemIds.isEmpty() ? Map.of()
                : itemMapper.selectBatchIds(itemIds).stream()
                        .collect(Collectors.toMap(Item::getId, Item::getTitle));
        return orders.stream().collect(Collectors.toMap(Order::getId,
                o -> itemTitle.getOrDefault(o.getItemId(), "已删除拍品")));
    }
}
