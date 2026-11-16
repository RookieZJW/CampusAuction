package com.campus.auction.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.auction.common.ResultCode;
import com.campus.auction.common.exception.BusinessException;
import com.campus.auction.dto.BidDTO;
import com.campus.auction.entity.AccountLog;
import com.campus.auction.entity.Auction;
import com.campus.auction.entity.BidRecord;
import com.campus.auction.entity.Item;
import com.campus.auction.entity.User;
import com.campus.auction.mapper.AccountLogMapper;
import com.campus.auction.mapper.AuctionMapper;
import com.campus.auction.mapper.BidRecordMapper;
import com.campus.auction.mapper.ItemMapper;
import com.campus.auction.mapper.UserMapper;
import com.campus.auction.service.AuctionCacheService;
import com.campus.auction.service.BidService;
import com.campus.auction.util.JsonUtil;
import com.campus.auction.vo.BidVO;
import com.campus.auction.vo.MyBidVO;import com.campus.auction.util.TimeUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 竞价服务实现
 *
 * 一致性设计（论文核心）：
 *  第一层 Redis Lua：原子「校验+改价」，单线程天然串行，拦截绝大多数无效出价；
 *  第二层 MySQL 乐观锁：UPDATE ... WHERE version=? AND current_price<?，
 *  影响行数=0 视为并发冲突，最多重试 3 次，保证任何情况下不会出现低价覆盖高价。
 *  Redis 不可用时自动降级为数据库单层校验。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BidServiceImpl implements BidService {

    /** 信用分出价门槛 */
    private static final int CREDIT_THRESHOLD = 60;
    /** 乐观锁冲突重试次数 */
    private static final int MAX_RETRY = 3;

    private final AuctionMapper auctionMapper;
    private final BidRecordMapper bidRecordMapper;
    private final UserMapper userMapper;
    private final ItemMapper itemMapper;
    private final AccountLogMapper accountLogMapper;
    private final AuctionCacheService cacheService;
    private final JsonUtil jsonUtil;
    private final TransactionTemplate transactionTemplate;

    @Override
    public BidVO bid(Long userId, Long auctionId, BidDTO dto) {
        int price = dto.getPrice();

        // 0. 用户资格校验
        User user = userMapper.selectById(userId);
        if (user == null || user.getStatus() != null && user.getStatus() == 1) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        if (user.getCreditScore() != null && user.getCreditScore() < CREDIT_THRESHOLD) {
            throw new BusinessException(ResultCode.LOW_CREDIT);
        }

        Auction snapshot = auctionMapper.selectById(auctionId);
        if (snapshot == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "拍卖不存在");
        }
        if (snapshot.getSellerId().equals(userId)) {
            throw new BusinessException(ResultCode.SELLER_CANNOT_BID);
        }

        // 1. Redis Lua 原子预校验（Redis 不可用时跳过，降级 DB 单层）
        boolean redisOn = cacheService.available();
        if (redisOn) {
            long r = cacheService.tryBid(auctionId, price, snapshot.getIncrement(), userId, snapshot.getSellerId());
            if (r == AuctionCacheService.LUA_CACHE_MISS) {
                cacheService.load(snapshot);
                r = cacheService.tryBid(auctionId, price, snapshot.getIncrement(), userId, snapshot.getSellerId());
            }
            switch ((int) r) {
                case (int) AuctionCacheService.LUA_NOT_ONGOING ->
                        throw new BusinessException(ResultCode.AUCTION_NOT_ONGOING);
                case (int) AuctionCacheService.LUA_SELF_BID ->
                        throw new BusinessException(ResultCode.SELF_OUTBID);
                case (int) AuctionCacheService.LUA_TOO_LOW -> {
                    long cur = snapshot.getCurrentPrice();
                    throw new BusinessException(ResultCode.BID_TOO_LOW,
                            "出价须不低于 " + ((cur + snapshot.getIncrement()) / 100.0) + " 元");
                }
                default -> { /* LUA_OK，进入数据库层 */ }
            }
        }

        // 2. 数据库事务 + 乐观锁（最多重试 3 次）
        for (int i = 0; i < MAX_RETRY; i++) {
            BidResult result;
            try {
                result = transactionTemplate.execute(status -> doBidInTx(userId, auctionId, price));
            } catch (ConcurrentBidException e) {
                // 并发冲突已回滚（含保证金冻结），重读快照后重试
                continue;
            }
            if (result != null && result.success) {
                Auction latest = result.auction;
                if (redisOn) {
                    // 落库成功后同步 Redis（含防狙击可能延长后的 endTime）
                    cacheService.syncAfterBid(latest);
                }
                return BidVO.builder()
                        .currentPrice(latest.getCurrentPrice())
                        .bidCount(latest.getBidCount())
                        .endTime(TimeUtil.str(latest.getEndTime()))
                        .minNext(latest.getCurrentPrice() + latest.getIncrement())
                        .isLeading(true)
                        .extended(result.extended)
                        .build();
            }
        }

        // 3. 重试耗尽：以 Redis 缓存与数据库对齐，返回冲突提示
        if (redisOn) {
            cacheService.load(auctionMapper.selectById(auctionId));
        }
        throw new BusinessException(ResultCode.BID_CONFLICT);
    }

    /** 事务内的单次出价尝试（乐观锁版本号 + 条件更新） */
    private BidResult doBidInTx(Long userId, Long auctionId, int price) {
        Auction a = auctionMapper.selectById(auctionId);
        if (a == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "拍卖不存在");
        }
        LocalDateTime now = LocalDateTime.now();

        // 二次校验（数据库为权威数据源）
        if (!Auction.STATUS_ONGOING.equals(a.getStatus()) || a.getEndTime().isBefore(now)) {
            throw new BusinessException(ResultCode.AUCTION_NOT_ONGOING);
        }
        if (userId.equals(a.getCurrentWinnerId())) {
            throw new BusinessException(ResultCode.SELF_OUTBID);
        }
        if (price < a.getCurrentPrice() + a.getIncrement()) {
            throw new BusinessException(ResultCode.BID_TOO_LOW,
                    "出价须不低于 " + ((a.getCurrentPrice() + a.getIncrement()) / 100.0) + " 元");
        }

        // 保证金冻结（本场首次出价的用户才冻结；SQL 原子扣减防并发透支）
        freezeDepositIfNeeded(userId, auctionId, a.getDeposit());

        // 防狙击判定：结束前 5 分钟内出价 → 延长 2 分钟（上限 3 次）
        boolean extended = false;
        LocalDateTime newEnd = a.getEndTime();
        int newExtendCount = a.getExtendCount();
        long remainMs = java.time.Duration.between(now, a.getEndTime()).toMillis();
        if (remainMs <= Auction.EXTEND_WINDOW_MS && a.getExtendCount() < Auction.MAX_EXTEND_COUNT) {
            newEnd = now.plusSeconds(Auction.EXTEND_DURATION_MS / 1000);
            newExtendCount = a.getExtendCount() + 1;
            extended = true;
        }

        // 乐观锁条件更新：版本未变 + 价格仍低于本次出价，才允许写入
        int updated = auctionMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Auction>()
                        .eq(Auction::getId, auctionId)
                        .eq(Auction::getVersion, a.getVersion())
                        .eq(Auction::getStatus, Auction.STATUS_ONGOING)
                        .lt(Auction::getCurrentPrice, price)
                        .set(Auction::getCurrentPrice, price)
                        .set(Auction::getCurrentWinnerId, userId)
                        .set(Auction::getBidCount, a.getBidCount() + 1)
                        .set(Auction::getVersion, a.getVersion() + 1)
                        .set(Auction::getEndTime, newEnd)
                        .set(Auction::getExtendCount, newExtendCount)
                        .set(Auction::getUpdateTime, now));
        if (updated == 0) {
            // 并发冲突：抛出后由外层捕获重试。注意保证金冻结需回滚 → 事务内抛异常自动回滚
            throw new ConcurrentBidException();
        }

        // 前领先者记录置为被超越
        if (a.getCurrentWinnerId() != null) {
            bidRecordMapper.update(null,
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<BidRecord>()
                            .eq(BidRecord::getAuctionId, auctionId)
                            .eq(BidRecord::getUserId, a.getCurrentWinnerId())
                            .eq(BidRecord::getStatus, BidRecord.STATUS_LEADING)
                            .set(BidRecord::getStatus, BidRecord.STATUS_OUTBID));
        }

        // 写入本次出价流水
        BidRecord record = new BidRecord();
        record.setAuctionId(auctionId);
        record.setUserId(userId);
        record.setPrice(price);
        record.setBidTime(now);
        record.setStatus(BidRecord.STATUS_LEADING);
        bidRecordMapper.insert(record);

        // 返回更新后的快照（供 Redis 同步）
        Auction latest = new Auction();
        latest.setId(auctionId);
        latest.setCurrentPrice(price);
        latest.setCurrentWinnerId(userId);
        latest.setBidCount(a.getBidCount() + 1);
        latest.setEndTime(newEnd);
        latest.setStatus(Auction.STATUS_ONGOING);
        latest.setExtendCount(newExtendCount);
        latest.setIncrement(a.getIncrement());
        latest.setSellerId(a.getSellerId());
        return new BidResult(true, latest, extended);
    }

    /** 首次参拍冻结保证金；原子扣减防并发透支 */
    private void freezeDepositIfNeeded(Long userId, Long auctionId, int deposit) {
        Long frozen = accountLogMapper.selectCount(
                new LambdaQueryWrapper<AccountLog>()
                        .eq(AccountLog::getUserId, userId)
                        .eq(AccountLog::getRefType, AccountLog.REF_AUCTION)
                        .eq(AccountLog::getRefId, auctionId)
                        .eq(AccountLog::getType, AccountLog.TYPE_FREEZE));
        if (frozen != null && frozen > 0) {
            return; // 本场已冻结过
        }
        int deducted = userMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<User>()
                        .eq(User::getId, userId)
                        .ge(User::getBalance, deposit)
                        .setSql("balance = balance - " + deposit));
        if (deducted == 0) {
            throw new BusinessException(ResultCode.INSUFFICIENT_BALANCE);
        }
        User u = userMapper.selectById(userId);
        AccountLog logEntry = new AccountLog();
        logEntry.setUserId(userId);
        logEntry.setType(AccountLog.TYPE_FREEZE);
        logEntry.setAmount(-deposit);
        logEntry.setBalanceAfter(u.getBalance());
        logEntry.setRefType(AccountLog.REF_AUCTION);
        logEntry.setRefId(auctionId);
        logEntry.setRemark("参拍冻结保证金");
        logEntry.setCreateTime(LocalDateTime.now());
        accountLogMapper.insert(logEntry);
    }

    @Override
    public List<MyBidVO> myBids(Long userId) {
        List<BidRecord> records = bidRecordMapper.selectList(
                new LambdaQueryWrapper<BidRecord>()
                        .eq(BidRecord::getUserId, userId)
                        .orderByDesc(BidRecord::getBidTime)
                        .last("LIMIT 50"));
        if (records.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> auctionIds = records.stream().map(BidRecord::getAuctionId).distinct().toList();
        Map<Long, Auction> auctionMap = auctionMapper.selectBatchIds(auctionIds).stream()
                .collect(Collectors.toMap(Auction::getId, Function.identity()));
        List<Long> itemIds = auctionMap.values().stream().map(Auction::getItemId).distinct().toList();
        Map<Long, Item> itemMap = itemIds.isEmpty() ? Map.of()
                : itemMapper.selectBatchIds(itemIds).stream()
                        .collect(Collectors.toMap(Item::getId, Function.identity()));

        return records.stream()
                .filter(r -> auctionMap.containsKey(r.getAuctionId()))
                .map(r -> {
                    Auction a = auctionMap.get(r.getAuctionId());
                    Item item = itemMap.get(a.getItemId());
                    List<String> images = item == null
                            ? Collections.emptyList() : jsonUtil.parseImages(item.getImages());
                    return MyBidVO.builder()
                            .auctionId(a.getId())
                            .itemId(a.getItemId())
                            .title(item != null ? item.getTitle() : null)
                            .coverImage(images.isEmpty() ? null : images.get(0))
                            .myPrice(r.getPrice())
                            .myStatus(r.getStatus())
                            .currentPrice(a.getCurrentPrice())
                            .bidCount(a.getBidCount())
                            .endTime(TimeUtil.str(a.getEndTime()))
                            .auctionStatus(a.getStatus())
                            .build();
                }).toList();
    }

    /** 事务内并发冲突信号（触发外层重试） */
    private static class ConcurrentBidException extends RuntimeException {
        ConcurrentBidException() {
            super("concurrent bid conflict", null, false, false);
        }
    }

    private record BidResult(boolean success, Auction auction, boolean extended) {
    }
}
