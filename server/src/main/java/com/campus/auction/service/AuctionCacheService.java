package com.campus.auction.service;

import com.campus.auction.entity.Auction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * 拍卖竞价 Redis 缓存 + Lua 原子预校验
 *
 * 缓存结构：HASH key = auction:bid:{auctionId}
 *   price       当前最高价（分）
 *   increment   加价幅度
 *   winner      当前领先者 userId
 *   seller      卖家 userId（禁止自拍）
 *   status      拍卖状态
 *   endTs       结束时间（epoch 毫秒）
 *
 * Lua 脚本在 Redis 单线程内原子执行「校验 + 改价」，
 * 把 90% 的无效出价挡在数据库之外（性能层）；
 * 数据库乐观锁做最终兜底（正确性层）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionCacheService {

    private static final String KEY_PREFIX = "auction:bid:";
    private static final Duration CACHE_TTL = Duration.ofHours(2);

    /** Lua 返回码 */
    public static final long LUA_OK = 1;
    public static final long LUA_NOT_ONGOING = -1;
    public static final long LUA_SELF_BID = -2;
    public static final long LUA_TOO_LOW = -3;
    public static final long LUA_CACHE_MISS = -10;

    /**
     * 原子出价预校验：通过则直接改写 Redis 价格与领先者
     * KEYS[1]=cacheKey, ARGV=[出价, 加价幅度, userId, 当前毫秒, 卖家userId]
     */
    private static final String BID_LUA = """
            local price = tonumber(ARGV[1])
            local increment = tonumber(ARGV[2])
            local userId = ARGV[3]
            local now = tonumber(ARGV[4])
            local seller = ARGV[5]

            local data = redis.call('HMGET', KEYS[1], 'price', 'winner', 'status', 'endTs')
            if not data[1] then return -10 end

            local current = tonumber(data[1])
            local winner = data[2]
            local status = data[3]
            local endTs = tonumber(data[4])

            if status ~= 'ONGOING' or endTs <= now then return -1 end
            if userId == winner or userId == seller then return -2 end
            if price < current + increment then return -3 end

            redis.call('HSET', KEYS[1], 'price', price, 'winner', userId)
            return 1
            """;

    private final StringRedisTemplate stringRedisTemplate;

    private final DefaultRedisScript<Long> bidScript = new DefaultRedisScript<>(BID_LUA, Long.class);

    private String key(Long auctionId) {
        return KEY_PREFIX + auctionId;
    }

    /** 从数据库加载拍卖竞价态到 Redis（首次出价或缓存失效时） */
    public void load(Auction auction) {
        String k = key(auction.getId());
        stringRedisTemplate.opsForHash().putAll(k, java.util.Map.of(
                "price", String.valueOf(auction.getCurrentPrice()),
                "increment", String.valueOf(auction.getIncrement()),
                "winner", auction.getCurrentWinnerId() == null ? "" : String.valueOf(auction.getCurrentWinnerId()),
                "seller", String.valueOf(auction.getSellerId()),
                "status", auction.getStatus(),
                "endTs", auction.getEndTime() == null ? "0" : String.valueOf(
                        auction.getEndTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli())
        ));
        stringRedisTemplate.expire(k, CACHE_TTL);
    }

    public boolean exists(Long auctionId) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key(auctionId)));
    }

    /**
     * 执行原子出价预校验
     *
     * @return LUA_OK / LUA_NOT_ONGOING / LUA_SELF_BID / LUA_TOO_LOW / LUA_CACHE_MISS
     */
    public long tryBid(Long auctionId, int price, int increment, Long userId, Long sellerId) {
        Long r = stringRedisTemplate.execute(bidScript,
                List.of(key(auctionId)),
                String.valueOf(price),
                String.valueOf(increment),
                String.valueOf(userId),
                String.valueOf(System.currentTimeMillis()),
                String.valueOf(sellerId));
        return r == null ? LUA_CACHE_MISS : r;
    }

    /** 出价成功落库后，同步 Redis（含防狙击延长后的结束时间） */
    public void syncAfterBid(Auction auction) {
        String k = key(auction.getId());
        if (!exists(auction.getId())) {
            load(auction);
            return;
        }
        stringRedisTemplate.opsForHash().putAll(k, java.util.Map.of(
                "price", String.valueOf(auction.getCurrentPrice()),
                "winner", String.valueOf(auction.getCurrentWinnerId()),
                "endTs", String.valueOf(auction.getEndTime().atZone(
                        java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()),
                "status", auction.getStatus(),
                "extendCount", String.valueOf(auction.getExtendCount())
        ));
        stringRedisTemplate.expire(k, CACHE_TTL);
    }

    /** 结算后更新状态；无缓存则忽略 */
    public void syncStatus(Long auctionId, String status) {
        String k = key(auctionId);
        if (exists(auctionId)) {
            stringRedisTemplate.opsForHash().put(k, "status", status);
        }
    }

    /** Redis 不可用时降级：出价走数据库单层，保证可用性 */
    public boolean available() {
        try {
            stringRedisTemplate.opsForValue().set("auction:ping", "1", Duration.ofSeconds(1));
            return true;
        } catch (Exception e) {
            log.warn("Redis 不可用，出价降级为数据库单层校验：{}", e.getMessage());
            return false;
        }
    }
}
