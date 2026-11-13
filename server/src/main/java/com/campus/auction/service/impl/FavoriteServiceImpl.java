package com.campus.auction.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.auction.common.ResultCode;
import com.campus.auction.common.exception.BusinessException;
import com.campus.auction.entity.Auction;
import com.campus.auction.entity.Category;
import com.campus.auction.entity.Favorite;
import com.campus.auction.entity.Item;
import com.campus.auction.mapper.AuctionMapper;
import com.campus.auction.mapper.CategoryMapper;
import com.campus.auction.mapper.FavoriteMapper;
import com.campus.auction.mapper.ItemMapper;
import com.campus.auction.service.FavoriteService;
import com.campus.auction.util.JsonUtil;
import com.campus.auction.vo.FavoriteVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FavoriteServiceImpl implements FavoriteService {

    private final FavoriteMapper favoriteMapper;
    private final AuctionMapper auctionMapper;
    private final ItemMapper itemMapper;
    private final CategoryMapper categoryMapper;
    private final JsonUtil jsonUtil;

    @Override
    public Map<String, Object> toggle(Long userId, Long auctionId) {
        Auction auction = auctionMapper.selectById(auctionId);
        if (auction == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "拍卖不存在");
        }
        Favorite existed = favoriteMapper.selectOne(
                new LambdaQueryWrapper<Favorite>()
                        .eq(Favorite::getUserId, userId)
                        .eq(Favorite::getAuctionId, auctionId));
        if (existed != null) {
            favoriteMapper.deleteById(existed.getId());
        } else {
            Favorite favorite = new Favorite();
            favorite.setUserId(userId);
            favorite.setAuctionId(auctionId);
            favorite.setCreateTime(LocalDateTime.now());
            favoriteMapper.insert(favorite);
        }
        long total = favoriteMapper.selectCount(
                new LambdaQueryWrapper<Favorite>().eq(Favorite::getUserId, userId));
        Map<String, Object> result = new HashMap<>();
        result.put("collected", existed == null);
        result.put("total", total);
        return result;
    }

    @Override
    public Map<String, Object> check(Long userId, Long auctionId) {
        Long count = favoriteMapper.selectCount(
                new LambdaQueryWrapper<Favorite>()
                        .eq(Favorite::getUserId, userId)
                        .eq(Favorite::getAuctionId, auctionId));
        Map<String, Object> result = new HashMap<>();
        result.put("collected", count > 0);
        return result;
    }

    @Override
    public List<FavoriteVO> mine(Long userId) {
        List<Favorite> favorites = favoriteMapper.selectList(
                new LambdaQueryWrapper<Favorite>()
                        .eq(Favorite::getUserId, userId)
                        .orderByDesc(Favorite::getCreateTime)
                        .last("LIMIT 50"));
        if (favorites.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> auctionIds = favorites.stream().map(Favorite::getAuctionId).distinct().toList();
        List<Auction> auctions = auctionMapper.selectBatchIds(auctionIds);
        Map<Long, Auction> auctionMap = auctions.stream()
                .collect(Collectors.toMap(Auction::getId, Function.identity()));

        List<Long> itemIds = auctions.stream().map(Auction::getItemId).distinct().toList();
        Map<Long, Item> itemMap = itemIds.isEmpty() ? Map.of()
                : itemMapper.selectBatchIds(itemIds).stream()
                        .collect(Collectors.toMap(Item::getId, Function.identity()));

        List<Long> categoryIds = itemMap.values().stream()
                .map(Item::getCategoryId).distinct().toList();
        Map<Long, String> categoryNames = categoryIds.isEmpty() ? Map.of()
                : categoryMapper.selectBatchIds(categoryIds).stream()
                        .collect(Collectors.toMap(Category::getId, Category::getName));

        LocalDateTime now = LocalDateTime.now();
        return favorites.stream()
                .filter(f -> auctionMap.containsKey(f.getAuctionId()))
                .map(f -> {
                    Auction auction = auctionMap.get(f.getAuctionId());
                    Item item = itemMap.get(auction.getItemId());
                    List<String> images = item == null ? List.of()
                            : jsonUtil.parseImages(item.getImages());
                    Long remainMs = Auction.STATUS_ONGOING.equals(auction.getStatus())
                            && auction.getEndTime() != null
                            ? Duration.between(now, auction.getEndTime()).toMillis() : null;
                    return FavoriteVO.builder()
                            .auctionId(auction.getId())
                            .title(item == null ? "已删除拍品" : item.getTitle())
                            .coverImage(images.isEmpty() ? null : images.get(0))
                            .categoryName(item == null ? null
                                    : categoryNames.get(item.getCategoryId()))
                            .currentPrice(auction.getCurrentPrice())
                            .bidCount(auction.getBidCount())
                            .status(auction.getStatus())
                            .remainMs(remainMs)
                            .build();
                }).toList();
    }
}
