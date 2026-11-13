package com.campus.auction.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campus.auction.entity.AccountLog;
import com.campus.auction.entity.Auction;
import com.campus.auction.entity.Notification;
import com.campus.auction.entity.Order;
import com.campus.auction.mapper.AccountLogMapper;
import com.campus.auction.mapper.AuctionMapper;
import com.campus.auction.mapper.ItemMapper;
import com.campus.auction.mapper.NotificationMapper;
import com.campus.auction.mapper.OrderMapper;
import com.campus.auction.mapper.UserMapper;
import com.campus.auction.entity.Item;
import com.campus.auction.service.AuctionCacheService;
import com.campus.auction.service.SettleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettleServiceImpl implements SettleService {

    private final AuctionMapper auctionMapper;
    private final OrderMapper orderMapper;
    private final AccountLogMapper accountLogMapper;
    private final UserMapper userMapper;
    private final ItemMapper itemMapper;
    private final NotificationMapper notificationMapper;
    private final AuctionCacheService cacheService;
    private final TransactionTemplate transactionTemplate;

    @Override
    public void settle(Long auctionId) {
        transactionTemplate.executeWithoutResult(status -> {
            Auction a = auctionMapper.selectById(auctionId);
            if (a == null || !Auction.STATUS_ONGOING.equals(a.getStatus())) {
                return;
            }
            if (a.getEndTime() == null || a.getEndTime().isAfter(LocalDateTime.now())) {
                return;
            }

            // CAS 抢占结算权：只有把 ONGOING 改成 SETTLING 的那个执行者继续处理
            int grabbed = auctionMapper.update(null, new LambdaUpdateWrapper<Auction>()
                    .eq(Auction::getId, auctionId)
                    .eq(Auction::getStatus, Auction.STATUS_ONGOING)
                    .set(Auction::getStatus, "SETTLING")
                    .set(Auction::getUpdateTime, LocalDateTime.now()));
            if (grabbed == 0) {
                return;
            }

            LocalDateTime now = LocalDateTime.now();
            boolean hasWinner = a.getBidCount() != null && a.getBidCount() > 0
                    && a.getCurrentWinnerId() != null;

            if (hasWinner) {
                // 成交：置 FINISHED + 幂等建单（uk_auction 唯一索引兜底）
                auctionMapper.update(null, new LambdaUpdateWrapper<Auction>()
                        .eq(Auction::getId, auctionId)
                        .eq(Auction::getStatus, "SETTLING")
                        .set(Auction::getStatus, Auction.STATUS_FINISHED)
                        .set(Auction::getUpdateTime, now));

                try {
                    Order order = new Order();
                    order.setOrderNo(generateOrderNo());
                    order.setAuctionId(auctionId);
                    order.setItemId(a.getItemId());
                    order.setBuyerId(a.getCurrentWinnerId());
                    order.setSellerId(a.getSellerId());
                    order.setDealPrice(a.getCurrentPrice());
                    order.setStatus(Order.STATUS_PENDING_TRADING);
                    order.setTradeType("OFFLINE");
                    order.setCreateTime(now);
                    order.setUpdateTime(now);
                    orderMapper.insert(order);
                } catch (DuplicateKeyException e) {
                    // 唯一索引兜底：该拍卖已生成过订单（理论上不会再走到，双保险）
                    log.warn("拍卖 {} 重复建单被唯一索引拦截", auctionId);
                }

                // 解冻非中标者的保证金
                unfreezeLosers(auctionId, a.getCurrentWinnerId(), a.getDeposit());

                // 通知买卖双方
                notify(a.getCurrentWinnerId(), Notification.TYPE_AUCTION_WIN,
                        "竞拍成功",
                        "您以 " + yuan(a.getCurrentPrice()) + " 元拍得「" + itemTitle(a.getItemId()) + "」，请在 48 小时内与卖家线下交割。",
                        auctionId);
                notify(a.getSellerId(), Notification.TYPE_AUCTION_END,
                        "拍卖成交",
                        "您的拍品「" + itemTitle(a.getItemId()) + "」以 " + yuan(a.getCurrentPrice()) + " 元成交，买家中标后需 48 小时内交割。",
                        auctionId);
            } else {
                // 流拍
                auctionMapper.update(null, new LambdaUpdateWrapper<Auction>()
                        .eq(Auction::getId, auctionId)
                        .eq(Auction::getStatus, "SETTLING")
                        .set(Auction::getStatus, Auction.STATUS_FAILED)
                        .set(Auction::getUpdateTime, now));
                notify(a.getSellerId(), Notification.TYPE_AUCTION_END,
                        "拍卖流拍",
                        "您的拍品「" + itemTitle(a.getItemId()) + "」到期无人出价，可调整价格后重新发布。",
                        auctionId);
            }

            cacheService.syncStatus(auctionId,
                    hasWinner ? Auction.STATUS_FINISHED : Auction.STATUS_FAILED);
            log.info("拍卖 {} 结算完成：{}", auctionId, hasWinner ? "成交" : "流拍");
        });
    }

    /** 解冻除中标者外所有参拍者的保证金（解冻动作天然幂等：FREEZE 流水只处理一次） */
    private void unfreezeLosers(Long auctionId, Long winnerId, int deposit) {
        if (winnerId == null) {
            return;
        }
        java.util.List<AccountLog> freezes = accountLogMapper.selectList(
                new LambdaQueryWrapper<AccountLog>()
                        .eq(AccountLog::getRefType, AccountLog.REF_AUCTION)
                        .eq(AccountLog::getRefId, auctionId)
                        .eq(AccountLog::getType, AccountLog.TYPE_FREEZE));
        for (AccountLog freeze : freezes) {
            if (freeze.getUserId().equals(winnerId)) {
                continue; // 中标者保证金保持冻结，交割完成后处理
            }
            userMapper.update(null, new LambdaUpdateWrapper<com.campus.auction.entity.User>()
                    .eq(com.campus.auction.entity.User::getId, freeze.getUserId())
                    .setSql("balance = balance + " + deposit));
            com.campus.auction.entity.User u = userMapper.selectById(freeze.getUserId());
            AccountLog unfreeze = new AccountLog();
            unfreeze.setUserId(freeze.getUserId());
            unfreeze.setType(AccountLog.TYPE_UNFREEZE);
            unfreeze.setAmount(deposit);
            unfreeze.setBalanceAfter(u.getBalance());
            unfreeze.setRefType(AccountLog.REF_AUCTION);
            unfreeze.setRefId(auctionId);
            unfreeze.setRemark("未中标，解冻保证金");
            unfreeze.setCreateTime(LocalDateTime.now());
            accountLogMapper.insert(unfreeze);
        }
    }

    private void notify(Long userId, String type, String title, String content, Long refId) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(title);
        n.setContent(content);
        n.setRefId(refId);
        n.setIsRead(0);
        n.setCreateTime(LocalDateTime.now());
        notificationMapper.insert(n);
    }

    private String itemTitle(Long itemId) {
        Item item = itemMapper.selectById(itemId);
        return item == null ? "已删除拍品" : item.getTitle();
    }

    private String yuan(int fen) {
        return String.format("%.2f", fen / 100.0);
    }

    private String generateOrderNo() {
        return "AU" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + ThreadLocalRandom.current().nextInt(1000, 9999);
    }
}
