package com.campus.auction.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.auction.entity.Auction;
import com.campus.auction.entity.BidRecord;
import com.campus.auction.entity.Category;
import com.campus.auction.entity.Item;
import com.campus.auction.entity.User;
import com.campus.auction.mapper.AuctionMapper;
import com.campus.auction.mapper.BidRecordMapper;
import com.campus.auction.mapper.CategoryMapper;
import com.campus.auction.mapper.ItemMapper;
import com.campus.auction.mapper.UserMapper;
import com.campus.auction.service.AuctionService;
import com.campus.auction.util.JsonUtil;
import com.campus.auction.vo.AuctionDetailVO;
import com.campus.auction.vo.AuctionListVO;
import com.campus.auction.vo.BidRecordVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuctionServiceImpl implements AuctionService {

    private final AuctionMapper auctionMapper;
    private final ItemMapper itemMapper;
    private final CategoryMapper categoryMapper;
    private final BidRecordMapper bidRecordMapper;
    private final UserMapper userMapper;
    private final JsonUtil jsonUtil;

    @Override
    public Map<String, Object> pageList(int page, int size, Long categoryId) {
        // 1. 先按拍卖表分页查 ONGOING（即将结束的排前面，制造紧迫感）
        Page<Auction> auctionPage = auctionMapper.selectPage(
                new Page<>(page, Math.min(size, 20)),
                new LambdaQueryWrapper<Auction>()
                        .eq(Auction::getStatus, Auction.STATUS_ONGOING)
                        .orderByAsc(Auction::getEndTime));

        List<Auction> auctions = auctionPage.getRecords();
        if (auctions.isEmpty()) {
            return Map.of("list", List.of(), "total", auctionPage.getTotal());
        }

        // 2. 批量补拍品与分类信息（避免 N+1 查询，论文可作性能优化点）
        List<Long> itemIds = auctions.stream().map(Auction::getItemId).toList();
        Map<Long, Item> itemMap = itemMapper.selectBatchIds(itemIds).stream()
                .collect(Collectors.toMap(Item::getId, Function.identity()));

        List<Long> categoryIds = itemMap.values().stream()
                .map(Item::getCategoryId).distinct().toList();
        Map<Long, String> categoryNameMap = categoryIds.isEmpty() ? Map.of()
                : categoryMapper.selectBatchIds(categoryIds).stream()
                        .collect(Collectors.toMap(Category::getId, Category::getName));

        List<AuctionListVO> list = auctions.stream()
                .filter(a -> itemMap.containsKey(a.getItemId()))
                .map(a -> {
                    Item item = itemMap.get(a.getItemId());
                    return AuctionListVO.builder()
                            .id(a.getId())
                            .title(item.getTitle())
                            .images(jsonUtil.parseImages(item.getImages()))
                            .categoryName(categoryNameMap.getOrDefault(item.getCategoryId(), "其他"))
                            .condition(item.getCondition())
                            .currentPrice(a.getCurrentPrice())
                            .bidCount(a.getBidCount())
                            .endTime(a.getEndTime().toString())
                            .build();
                }).toList();

        return Map.of("list", list, "total", auctionPage.getTotal());
    }

    @Override
    public AuctionDetailVO detail(Long auctionId) {
        Auction auction = auctionMapper.selectById(auctionId);
        if (auction == null) {
            return null;
        }
        Item item = itemMapper.selectById(auction.getItemId());
        Category category = item != null ? categoryMapper.selectById(item.getCategoryId()) : null;
        User seller = userMapper.selectById(auction.getSellerId());

        // 出价记录：最新在前，取前 20 条
        List<BidRecord> records = bidRecordMapper.selectList(
                new LambdaQueryWrapper<BidRecord>()
                        .eq(BidRecord::getAuctionId, auctionId)
                        .orderByDesc(BidRecord::getPrice)
                        .last("LIMIT 20"));

        List<Long> bidderIds = records.stream().map(BidRecord::getUserId).distinct().toList();
        Map<Long, String> nicknameMap = bidderIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(bidderIds).stream()
                        .collect(Collectors.toMap(User::getId,
                                u -> u.getNickname() == null ? "微信用户" : u.getNickname()));

        List<BidRecordVO> recordVOs = records.stream()
                .map(r -> BidRecordVO.builder()
                        .nickname(maskNickname(nicknameMap.getOrDefault(r.getUserId(), "微信用户")))
                        .price(r.getPrice())
                        .bidTime(r.getBidTime() == null ? null : r.getBidTime().toString())
                        .status(r.getStatus())
                        .build())
                .toList();

        return AuctionDetailVO.builder()
                .id(auction.getId())
                .itemId(item != null ? item.getId() : null)
                .title(item != null ? item.getTitle() : null)
                .description(item != null ? item.getDescription() : null)
                .images(item != null ? jsonUtil.parseImages(item.getImages()) : List.of())
                .categoryName(category != null ? category.getName() : "其他")
                .condition(item != null ? item.getCondition() : null)
                .startPrice(auction.getStartPrice())
                .increment(auction.getIncrement())
                .deposit(auction.getDeposit())
                .currentPrice(auction.getCurrentPrice())
                .bidCount(auction.getBidCount())
                .status(auction.getStatus())
                .startTime(auction.getStartTime() == null ? null : auction.getStartTime().toString())
                .endTime(auction.getEndTime() == null ? null : auction.getEndTime().toString())
                .sellerNickname(seller != null ? seller.getNickname() : null)
                .sellerAvatar(seller != null ? seller.getAvatar() : null)
                .bidRecords(recordVOs)
                .myLeading(false)
                .build();
    }

    /** 昵称脱敏：保留首尾字符，如「张三」→「张*」，「微信用户」→「微**户」 */
    private String maskNickname(String nickname) {
        if (nickname == null || nickname.length() <= 2) {
            return nickname;
        }
        return nickname.charAt(0) + "*".repeat(nickname.length() - 2) + nickname.charAt(nickname.length() - 1);
    }
}
