package com.campus.auction.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campus.auction.entity.CreditLog;
import com.campus.auction.entity.Notification;
import com.campus.auction.entity.Order;
import com.campus.auction.entity.User;
import com.campus.auction.mapper.CreditLogMapper;
import com.campus.auction.mapper.NotificationMapper;
import com.campus.auction.mapper.OrderMapper;
import com.campus.auction.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单超时违约处理：
 * 待交割订单 48 小时未完成 → 订单 CANCELLED + 没收买方已冻结保证金 + 买方信用分-10 + 通知
 *
 * 与结算任务一致：先 CAS 抢占（PENDING_TRADING→处理中）防多实例重复处理；
 * 保证金在出价时已从余额扣除，违约即没收，无需再动余额，仅记账留痕。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutTask {

    private static final int VIOLATION_CREDIT_PENALTY = 10;
    private static final long TIMEOUT_HOURS = 48;

    private final OrderMapper orderMapper;
    private final UserMapper userMapper;
    private final CreditLogMapper creditLogMapper;
    private final NotificationMapper notificationMapper;
    private final com.campus.auction.mapper.AccountLogMapper accountLogMapper;
    private final TransactionTemplate transactionTemplate;

    /** 每小时检查一次（演示可用 SQL 手工把 create_time 改到 48h 前触发） */
    @Scheduled(fixedDelay = 3_600_000, initialDelay = 60_000)
    public void cancelTimeoutOrders() {
        List<Order> timeouts = orderMapper.selectList(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getStatus, Order.STATUS_PENDING_TRADING)
                        .le(Order::getCreateTime, LocalDateTime.now().minusHours(TIMEOUT_HOURS))
                        .last("LIMIT 50"));
        if (timeouts.isEmpty()) {
            return;
        }
        log.info("违约检查：发现 {} 笔超时订单", timeouts.size());
        for (Order order : timeouts) {
            try {
                cancelOne(order);
            } catch (Exception e) {
                log.error("订单 {} 违约处理异常", order.getId(), e);
            }
        }
    }

    private void cancelOne(Order order) {
        transactionTemplate.executeWithoutResult(status -> {
            LocalDateTime now = LocalDateTime.now();
            int grabbed = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                    .eq(Order::getId, order.getId())
                    .eq(Order::getStatus, Order.STATUS_PENDING_TRADING)
                    .set(Order::getStatus, Order.STATUS_CANCELLED)
                    .set(Order::getUpdateTime, now));
            if (grabbed == 0) {
                return;
            }

            // 没收买方已冻结保证金（出价时已扣余额，此处仅记账留痕）
            com.campus.auction.entity.AccountLog deduct = new com.campus.auction.entity.AccountLog();
            deduct.setUserId(order.getBuyerId());
            deduct.setType(com.campus.auction.entity.AccountLog.TYPE_DEDUCT);
            deduct.setAmount(0);
            User buyer = userMapper.selectById(order.getBuyerId());
            deduct.setBalanceAfter(buyer == null ? 0 : buyer.getBalance());
            deduct.setRefType(com.campus.auction.entity.AccountLog.REF_AUCTION);
            deduct.setRefId(order.getAuctionId());
            deduct.setRemark("违约，没收已冻结保证金");
            deduct.setCreateTime(now);
            accountLogMapper.insert(deduct);

            // 买方信用分 -10
            userMapper.update(null, new LambdaUpdateWrapper<User>()
                    .eq(User::getId, order.getBuyerId())
                    .setSql("credit_score = GREATEST(credit_score - " + VIOLATION_CREDIT_PENALTY + ", 0)"));
            CreditLog creditLog = new CreditLog();
            creditLog.setUserId(order.getBuyerId());
            creditLog.setChangeValue(-VIOLATION_CREDIT_PENALTY);
            creditLog.setReason("中标后 48 小时未完成交割");
            creditLog.setRefId(order.getId());
            creditLog.setCreateTime(now);
            creditLogMapper.insert(creditLog);

            // 通知双方
            notify(order.getBuyerId(), "订单违约",
                    "订单 " + order.getOrderNo() + " 超时未交割已被取消，保证金已没收，信用分 -10。");
            notify(order.getSellerId(), "买方违约",
                    "订单 " + order.getOrderNo() + " 买方超时未交割已取消，拍品可重新发布。");
            log.info("订单 {} 违约处理完成", order.getId());
        });
    }

    private void notify(Long userId, String title, String content) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(Notification.TYPE_AUCTION_END);
        n.setTitle(title);
        n.setContent(content);
        n.setIsRead(0);
        n.setCreateTime(LocalDateTime.now());
        notificationMapper.insert(n);
    }
}
