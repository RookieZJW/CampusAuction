package com.campus.auction.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campus.auction.common.ResultCode;
import com.campus.auction.common.exception.BusinessException;
import com.campus.auction.entity.AccountLog;
import com.campus.auction.entity.Auction;
import com.campus.auction.entity.CreditLog;
import com.campus.auction.entity.Item;
import com.campus.auction.entity.Notification;
import com.campus.auction.entity.Order;
import com.campus.auction.entity.User;
import com.campus.auction.mapper.AccountLogMapper;
import com.campus.auction.mapper.AuctionMapper;
import com.campus.auction.mapper.CreditLogMapper;
import com.campus.auction.mapper.ItemMapper;
import com.campus.auction.mapper.NotificationMapper;
import com.campus.auction.mapper.OrderMapper;
import com.campus.auction.mapper.UserMapper;
import com.campus.auction.service.OrderService;
import com.campus.auction.util.JsonUtil;
import com.campus.auction.vo.OrderVO;import com.campus.auction.util.TimeUtil;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    /** 正常完成交易双方信用分奖励 */
    private static final int CREDIT_REWARD = 2;

    private final OrderMapper orderMapper;
    private final ItemMapper itemMapper;
    private final AuctionMapper auctionMapper;
    private final UserMapper userMapper;
    private final AccountLogMapper accountLogMapper;
    private final CreditLogMapper creditLogMapper;
    private final NotificationMapper notificationMapper;
    private final JsonUtil jsonUtil;
    private final TransactionTemplate transactionTemplate;

    @Override
    public List<OrderVO> myOrders(Long userId) {
        List<Order> orders = orderMapper.selectList(
                new LambdaQueryWrapper<Order>()
                        .in(Order::getStatus, Order.STATUS_PENDING_TRADING, Order.STATUS_COMPLETED, Order.STATUS_CANCELLED)
                        .and(w -> w.eq(Order::getBuyerId, userId).or().eq(Order::getSellerId, userId))
                        .orderByDesc(Order::getCreateTime)
                        .last("LIMIT 50"));
        if (orders.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> itemIds = orders.stream().map(Order::getItemId).distinct().toList();
        Map<Long, Item> itemMap = itemMapper.selectBatchIds(itemIds).stream()
                .collect(Collectors.toMap(Item::getId, Function.identity()));
        List<Long> userIds = orders.stream()
                .flatMap(o -> java.util.stream.Stream.of(o.getBuyerId(), o.getSellerId())).distinct().toList();
        Map<Long, String> nicknameMap = userIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId,
                                u -> u.getNickname() == null ? "微信用户" : u.getNickname()));

        return orders.stream().map(o -> {
            Item item = itemMap.get(o.getItemId());
            List<String> images = item == null ? List.of() : jsonUtil.parseImages(item.getImages());
            boolean isBuyer = o.getBuyerId().equals(userId);
            return OrderVO.builder()
                    .orderId(o.getId())
                    .orderNo(o.getOrderNo())
                    .role(isBuyer ? "BUYER" : "SELLER")
                    .itemId(o.getItemId())
                    .title(item != null ? item.getTitle() : "已删除拍品")
                    .coverImage(images.isEmpty() ? null : images.get(0))
                    .dealPrice(o.getDealPrice())
                    .status(o.getStatus())
                    .tradePlace(o.getTradePlace())
                    .counterparty(nicknameMap.getOrDefault(
                            isBuyer ? o.getSellerId() : o.getBuyerId(), "微信用户"))
                    .createTime(TimeUtil.str(o.getCreateTime()))
                    .build();
        }).toList();
    }

    @Override
    public void completeTrade(Long orderId, Long userId) {
        transactionTemplate.executeWithoutResult(status -> {
            Order order = loadOrder(orderId);
            if (!order.getBuyerId().equals(userId)) {
                throw new BusinessException(ResultCode.FORBIDDEN, "仅买方可确认完成交割");
            }
            if (!Order.STATUS_PENDING_TRADING.equals(order.getStatus())) {
                throw new BusinessException(ResultCode.BIZ_ERROR, "订单不在待交割状态");
            }

            LocalDateTime now = LocalDateTime.now();
            // CAS 防重复确认
            int updated = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                    .eq(Order::getId, orderId)
                    .eq(Order::getStatus, Order.STATUS_PENDING_TRADING)
                    .set(Order::getStatus, Order.STATUS_COMPLETED)
                    .set(Order::getCompletedAt, now)
                    .set(Order::getUpdateTime, now));
            if (updated == 0) {
                throw new BusinessException(ResultCode.BIZ_ERROR, "订单已被处理");
            }

            // 解冻中标者保证金
            unfreezeBuyerDeposit(order);
            // 双方信用分 +2
            rewardCredit(order.getBuyerId(), order.getId());
            rewardCredit(order.getSellerId(), order.getId());
            // 通知双方
            Item item = itemMapper.selectById(order.getItemId());
            String title = item == null ? "拍品" : item.getTitle();
            notify(order.getBuyerId(), "订单完成", "「" + title + "」交易完成，保证金已解冻，信用分 +2。", order.getId());
            notify(order.getSellerId(), "订单完成", "「" + title + "」交易完成，买方已确认，信用分 +2。", order.getId());
        });
    }

    @Override
    public void updateTradePlace(Long orderId, Long userId, String place) {
        Order order = loadOrder(orderId);
        if (!order.getBuyerId().equals(userId) && !order.getSellerId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        if (!Order.STATUS_PENDING_TRADING.equals(order.getStatus())) {
            throw new BusinessException(ResultCode.BIZ_ERROR, "订单不在待交割状态");
        }
        orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, orderId)
                .set(Order::getTradePlace, place)
                .set(Order::getUpdateTime, LocalDateTime.now()));
        // 通知对手方
        boolean isBuyer = order.getBuyerId().equals(userId);
        Long other = isBuyer ? order.getSellerId() : order.getBuyerId();
        notify(other, "交割地点更新", "对方将交割地点更新为：" + place, order.getId());
    }

    /** 解冻中标者（买方）保证金：找该拍卖的 FREEZE 流水，退回等额 */
    private void unfreezeBuyerDeposit(Order order) {
        List<AccountLog> freezes = accountLogMapper.selectList(
                new LambdaQueryWrapper<AccountLog>()
                        .eq(AccountLog::getRefType, AccountLog.REF_AUCTION)
                        .eq(AccountLog::getRefId, order.getAuctionId())
                        .eq(AccountLog::getUserId, order.getBuyerId())
                        .eq(AccountLog::getType, AccountLog.TYPE_FREEZE));
        int deposit = freezes.isEmpty() ? 1000 : Math.abs(freezes.get(0).getAmount());
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, order.getBuyerId())
                .setSql("balance = balance + " + deposit));
        User buyer = userMapper.selectById(order.getBuyerId());
        AccountLog unfreeze = new AccountLog();
        unfreeze.setUserId(order.getBuyerId());
        unfreeze.setType(AccountLog.TYPE_UNFREEZE);
        unfreeze.setAmount(deposit);
        unfreeze.setBalanceAfter(buyer.getBalance());
        unfreeze.setRefType(AccountLog.REF_AUCTION);
        unfreeze.setRefId(order.getAuctionId());
        unfreeze.setRemark("交易完成，解冻保证金");
        unfreeze.setCreateTime(LocalDateTime.now());
        accountLogMapper.insert(unfreeze);
    }

    private void rewardCredit(Long userId, Long orderId) {
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .setSql("credit_score = credit_score + " + CREDIT_REWARD));
        CreditLog log = new CreditLog();
        log.setUserId(userId);
        log.setChangeValue(CREDIT_REWARD);
        log.setReason("正常完成交易");
        log.setRefId(orderId);
        log.setCreateTime(LocalDateTime.now());
        creditLogMapper.insert(log);
    }

    private void notify(Long userId, String title, String content, Long refId) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(Notification.TYPE_AUCTION_END);
        n.setTitle(title);
        n.setContent(content);
        n.setRefId(refId);
        n.setIsRead(0);
        n.setCreateTime(LocalDateTime.now());
        notificationMapper.insert(n);
    }

    private Order loadOrder(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "订单不存在");
        }
        return order;
    }
}
