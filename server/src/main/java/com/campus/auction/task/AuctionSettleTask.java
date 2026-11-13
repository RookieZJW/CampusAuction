package com.campus.auction.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.auction.entity.Auction;
import com.campus.auction.mapper.AuctionMapper;
import com.campus.auction.service.SettleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 拍卖到期结算任务
 *
 * 1. 每 30 秒扫描 status=ONGOING 且 end_time 已过的拍卖；
 * 2. Redis 分布式锁（SETNX，25s 过期）防多实例重复调度；
 * 3. 结算内部再用 CAS（ONGOING→SETTLING）+ 订单唯一索引双保险保证幂等。
 *
 * 潜在的更优方案（论文「展望」）：Redis ZSet 延时队列，score=end_time，
 * 精度可到秒级；本实现的 30 秒扫描粒度对演示与校园场景已足够。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionSettleTask {

    private static final String LOCK_KEY = "lock:auction:settle";

    private final AuctionMapper auctionMapper;
    private final SettleService settleService;
    private final StringRedisTemplate stringRedisTemplate;

    @Scheduled(fixedDelay = 30_000, initialDelay = 15_000)
    public void settleExpiredAuctions() {
        Boolean locked = stringRedisTemplate.opsForValue()
                .setIfAbsent(LOCK_KEY, "1", Duration.ofSeconds(25));
        if (!Boolean.TRUE.equals(locked)) {
            return;
        }
        try {
            List<Auction> expired = auctionMapper.selectList(
                    new LambdaQueryWrapper<Auction>()
                            .eq(Auction::getStatus, Auction.STATUS_ONGOING)
                            .le(Auction::getEndTime, LocalDateTime.now())
                            .last("LIMIT 100"));
            if (expired.isEmpty()) {
                return;
            }
            log.info("结算任务：发现 {} 场到期拍卖", expired.size());
            for (Auction auction : expired) {
                try {
                    settleService.settle(auction.getId());
                } catch (Exception e) {
                    log.error("拍卖 {} 结算异常", auction.getId(), e);
                }
            }
        } finally {
            stringRedisTemplate.delete(LOCK_KEY);
        }
    }
}
