package com.campus.auction.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campus.auction.common.ResultCode;
import com.campus.auction.common.exception.BusinessException;
import com.campus.auction.entity.Item;
import com.campus.auction.entity.Notification;
import com.campus.auction.entity.Order;
import com.campus.auction.mapper.ItemMapper;
import com.campus.auction.mapper.NotificationMapper;
import com.campus.auction.mapper.OrderMapper;
import com.campus.auction.payment.PaymentContext;
import com.campus.auction.payment.PaymentNotify;
import com.campus.auction.payment.PaymentProvider;
import com.campus.auction.service.PaymentService;
import com.campus.auction.util.TimeUtil;
import com.campus.auction.vo.PayPrepareVO;
import com.campus.auction.vo.PayStatusVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 支付编排实现（方案 B）。
 *
 * <p>三条硬约束：
 * <ol>
 *   <li>支付状态与交割状态是两个字段，互不覆盖：t_order.pay_status / status；</li>
 *   <li>所有状态流转用 CAS（WHERE pay_status = 期望值），重复回调天然幂等；</li>
 *   <li>模拟支付不改变 t_user.balance，也不写 t_account_log——不污染虚拟保证金账。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private static final DateTimeFormatter NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final OrderMapper orderMapper;
    private final ItemMapper itemMapper;
    private final NotificationMapper notificationMapper;
    private final PaymentProvider paymentProvider;
    private final TransactionTemplate transactionTemplate;

    @Override
    public PayPrepareVO prepare(Long orderId, Long userId) {
        Order order = loadOrder(orderId);
        assertBuyer(order, userId);
        if (!Order.STATUS_PENDING_TRADING.equals(order.getStatus())) {
            throw new BusinessException(ResultCode.PAY_NOT_ALLOWED, "订单不在待交割状态，无法支付");
        }
        // 已支付：不允许重复发起
        if (Order.PAY_PAID.equals(order.getPayStatus())) {
            throw new BusinessException(ResultCode.PAY_STATUS_CONFLICT, "该订单已完成支付");
        }
        // 支付中：幂等返回已有支付单，避免重复生成 pay_no
        if (Order.PAY_PAYING.equals(order.getPayStatus()) && order.getPayNo() != null) {
            return buildPrepare(order);
        }

        String payNo = generatePayNo();
        LocalDateTime now = LocalDateTime.now();
        int updated = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, orderId)
                .in(Order::getPayStatus, Order.PAY_UNPAID, Order.PAY_CLOSED)
                .set(Order::getPayStatus, Order.PAY_PAYING)
                .set(Order::getPayNo, payNo)
                .set(Order::getPayProvider, paymentProvider.code())
                .set(Order::getPayAmount, order.getDealPrice())
                .set(Order::getUpdateTime, now));
        if (updated == 0) {
            // 并发兜底：重新读取后按最新状态返回
            return buildPrepare(loadOrder(orderId));
        }
        return buildPrepare(loadOrder(orderId));
    }

    @Override
    public PayStatusVO confirm(Long orderId, Long userId) {
        Order order = loadOrder(orderId);
        assertBuyer(order, userId);
        // 重复点击：已支付直接幂等返回
        if (Order.PAY_PAID.equals(order.getPayStatus())) {
            return toStatus(order);
        }
        if (!Order.PAY_PAYING.equals(order.getPayStatus()) || order.getPayNo() == null) {
            throw new BusinessException(ResultCode.PAY_STATUS_CONFLICT, "请先发起支付");
        }
        // 关键：模拟支付复用同一条回调链路，让「验签 → 幂等 → CAS」在演示时真实跑通
        return handleNotify(PaymentNotify.builder()
                .provider(paymentProvider.code())
                .payNo(order.getPayNo())
                .transactionId(generateTransactionId())
                .rawBody("mock-client-confirm")
                .build());
    }

    @Override
    public PayStatusVO close(Long orderId, Long userId) {
        Order order = loadOrder(orderId);
        assertBuyer(order, userId);
        if (Order.PAY_PAID.equals(order.getPayStatus())) {
            throw new BusinessException(ResultCode.PAY_STATUS_CONFLICT, "已支付订单不可关闭支付单");
        }
        int updated = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, orderId)
                .in(Order::getPayStatus, Order.PAY_PAYING, Order.PAY_UNPAID)
                .set(Order::getPayStatus, Order.PAY_CLOSED)
                .set(Order::getUpdateTime, LocalDateTime.now()));
        if (updated == 0) {
            throw new BusinessException(ResultCode.PAY_STATUS_CONFLICT);
        }
        return toStatus(loadOrder(orderId));
    }

    @Override
    public PayStatusVO status(Long orderId, Long userId) {
        Order order = loadOrder(orderId);
        if (!order.getBuyerId().equals(userId) && !order.getSellerId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        return toStatus(order);
    }

    @Override
    public PayStatusVO handleNotify(PaymentNotify notify) {
        PaymentProvider provider = resolveProvider(notify.getProvider());
        if (!provider.verifyNotify(notify)) {
            throw new BusinessException(ResultCode.PAY_PROVIDER_UNAVAILABLE, "支付回调验签失败");
        }

        return transactionTemplate.execute(txStatus -> {
            Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                    .eq(Order::getPayNo, notify.getPayNo())
                    .last("LIMIT 1"));
            if (order == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "支付单不存在");
            }
            // 幂等：重复回调直接返回成功，不重复通知、不重复记账
            if (Order.PAY_PAID.equals(order.getPayStatus())) {
                return toStatus(order);
            }
            if (!Order.PAY_PAYING.equals(order.getPayStatus())) {
                throw new BusinessException(ResultCode.PAY_STATUS_CONFLICT, "支付单状态不允许置为已支付");
            }

            LocalDateTime now = LocalDateTime.now();
            int updated = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                    .eq(Order::getId, order.getId())
                    .eq(Order::getPayStatus, Order.PAY_PAYING)
                    .set(Order::getPayStatus, Order.PAY_PAID)
                    .set(Order::getTransactionId, notify.getTransactionId())
                    .set(Order::getPaidAt, now)
                    .set(Order::getUpdateTime, now));
            if (updated == 0) {
                Order fresh = loadOrder(order.getId());
                if (Order.PAY_PAID.equals(fresh.getPayStatus())) {
                    return toStatus(fresh); // 并发下已被其他回调处理，幂等成功
                }
                throw new BusinessException(ResultCode.PAY_STATUS_CONFLICT);
            }

            // 通知买卖双方（模拟支付成功不改变虚拟余额，仅作履约提示）
            Item item = itemMapper.selectById(order.getItemId());
            String title = item == null ? "拍品" : item.getTitle();
            String amountYuan = yuan(order.getDealPrice());
            notifyUser(order.getBuyerId(), "支付成功",
                    "「" + title + "」已支付 ¥" + amountYuan + "（模拟支付），支付单号 " + order.getPayNo()
                            + "。请与卖家约定线下交割地点。", order.getId());
            notifyUser(order.getSellerId(), "买家已支付",
                    "买家已完成「" + title + "」的支付（模拟支付），成交价 ¥" + amountYuan
                            + "，请准备线下交割。", order.getId());
            log.info("支付成功（模拟）：orderId={}, payNo={}, transactionId={}",
                    order.getId(), order.getPayNo(), notify.getTransactionId());
            return toStatus(loadOrder(order.getId()));
        });
    }

    // ------------------------------------------------------------------
    // 私有辅助
    // ------------------------------------------------------------------

    private PayPrepareVO buildPrepare(Order order) {
        if (!Order.PAY_PAYING.equals(order.getPayStatus()) || order.getPayNo() == null) {
            throw new BusinessException(ResultCode.PAY_STATUS_CONFLICT);
        }
        Item item = itemMapper.selectById(order.getItemId());
        String title = item == null ? "校园拍场成交订单" : item.getTitle();
        Integer amount = order.getPayAmount() == null ? order.getDealPrice() : order.getPayAmount();
        return paymentProvider.prepare(PaymentContext.builder()
                .orderId(order.getId())
                .orderNo(order.getOrderNo())
                .payNo(order.getPayNo())
                .amount(amount)
                .title(title)
                .notifyUrl("/api/v1/pay/notify/" + paymentProvider.code().toLowerCase())
                .build());
    }

    private PaymentProvider resolveProvider(String code) {
        if (code != null && !code.isBlank() && !paymentProvider.code().equalsIgnoreCase(code)) {
            throw new BusinessException(ResultCode.PAY_PROVIDER_UNAVAILABLE, "回调渠道与当前支付网关不一致");
        }
        return paymentProvider;
    }

    private PayStatusVO toStatus(Order o) {
        Item item = itemMapper.selectById(o.getItemId());
        String title = item == null ? "校园拍场成交订单" : item.getTitle();
        return PayStatusVO.builder()
                .orderId(o.getId())
                .orderNo(o.getOrderNo())
                .title(title)
                .payStatus(o.getPayStatus() == null ? Order.PAY_UNPAID : o.getPayStatus())
                .payNo(o.getPayNo())
                .provider(o.getPayProvider())
                .amount(o.getPayAmount() == null ? o.getDealPrice() : o.getPayAmount())
                .transactionId(o.getTransactionId())
                .paidAt(o.getPaidAt() == null ? "" : TimeUtil.str(o.getPaidAt()))
                .build();
    }

    private void assertBuyer(Order order, Long userId) {
        if (!order.getBuyerId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅买方可发起支付");
        }
    }

    private Order loadOrder(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "订单不存在");
        }
        return order;
    }

    private void notifyUser(Long userId, String title, String content, Long refId) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(Notification.TYPE_PAYMENT);
        n.setTitle(title);
        n.setContent(content);
        n.setRefId(refId);
        n.setIsRead(0);
        n.setCreateTime(LocalDateTime.now());
        notificationMapper.insert(n);
    }

    private String yuan(int fen) {
        return String.format("%.2f", fen / 100.0);
    }

    private String generatePayNo() {
        return "PAY" + LocalDateTime.now().format(NO_FMT)
                + ThreadLocalRandom.current().nextInt(1000, 9999);
    }

    private String generateTransactionId() {
        return "MOCK" + LocalDateTime.now().format(NO_FMT)
                + ThreadLocalRandom.current().nextInt(100000, 999999);
    }
}
