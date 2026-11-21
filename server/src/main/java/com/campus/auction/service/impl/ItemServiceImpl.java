package com.campus.auction.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.auction.common.ResultCode;
import com.campus.auction.common.exception.BusinessException;
import com.campus.auction.dto.PublishItemDTO;
import com.campus.auction.entity.Auction;
import com.campus.auction.entity.Item;
import com.campus.auction.entity.School;
import com.campus.auction.entity.User;
import com.campus.auction.mapper.AuctionMapper;
import com.campus.auction.mapper.CategoryMapper;
import com.campus.auction.mapper.ItemMapper;
import com.campus.auction.mapper.SchoolMapper;
import com.campus.auction.mapper.UserMapper;
import com.campus.auction.service.ItemService;
import com.campus.auction.util.JsonUtil;
import com.campus.auction.vo.MyItemVO;import com.campus.auction.util.TimeUtil;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ItemServiceImpl implements ItemService {

    /** 参拍保证金固定 10 元（分），W3 冻结逻辑接入 */
    private static final int DEFAULT_DEPOSIT = 1000;

    private final ItemMapper itemMapper;
    private final AuctionMapper auctionMapper;
    private final CategoryMapper categoryMapper;
    private final UserMapper userMapper;
    private final SchoolMapper schoolMapper;
    private final JsonUtil jsonUtil;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long publish(Long userId, PublishItemDTO dto) {
        // 多校隔离：发布必须符合学校认证，拍品归属卖家学校
        User seller = userMapper.selectById(userId);
        if (seller == null || seller.getSchoolId() == null) {
            throw new BusinessException(ResultCode.SCHOOL_NOT_AUTHED);
        }
        Long schoolId = seller.getSchoolId();

        // 前端显式携带学校信息；与登录用户认证学校不一致直接拒绝，防止挂错学校
        if (dto.getSchoolId() != null && !schoolId.equals(dto.getSchoolId())) {
            throw new BusinessException(ResultCode.CROSS_SCHOOL_DENIED,
                    "发布学校与当前认证学校不一致，请重新进入发布页");
        }

        // 校验分类存在
        if (categoryMapper.selectById(dto.getCategoryId()) == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "分类不存在");
        }

        // 1. 物品入库（PENDING 待审）
        Item item = new Item();
        item.setSellerId(userId);
        item.setSchoolId(schoolId);
        item.setCategoryId(dto.getCategoryId());
        item.setTitle(dto.getTitle());
        item.setDescription(dto.getDescription());
        item.setImages(jsonUtil.writeImages(dto.getImages()));
        item.setCondition(dto.getCondition());
        item.setStatus(Item.STATUS_PENDING);
        item.setCreateTime(LocalDateTime.now());
        item.setUpdateTime(LocalDateTime.now());
        itemMapper.insert(item);

        // 2. 拍卖场次入库（PENDING，审核通过时才转 ONGOING 并计算起止时间）
        Auction auction = new Auction();
        auction.setItemId(item.getId());
        auction.setSellerId(userId);
        auction.setSchoolId(schoolId);
        auction.setStartPrice(dto.getStartPrice());
        auction.setIncrement(dto.getIncrement());
        auction.setDeposit(DEFAULT_DEPOSIT);
        auction.setDurationMinutes(dto.getDurationHours() * 60);
        auction.setCurrentPrice(dto.getStartPrice());
        auction.setBidCount(0);
        auction.setExtendCount(0);
        auction.setVersion(0);
        auction.setStatus(Auction.STATUS_PENDING);
        auction.setCreateTime(LocalDateTime.now());
        auction.setUpdateTime(LocalDateTime.now());
        auctionMapper.insert(auction);

        return item.getId();
    }

    @Override
    public List<MyItemVO> myItems(Long userId) {
        List<Item> items = itemMapper.selectList(
                new LambdaQueryWrapper<Item>()
                        .eq(Item::getSellerId, userId)
                        .orderByDesc(Item::getUpdateTime));

        if (items.isEmpty()) {
            return Collections.emptyList();
        }

        // 批量取拍卖场次，避免逐条查询
        List<Long> itemIds = items.stream().map(Item::getId).toList();
        Map<Long, Auction> auctionMap = auctionMapper.selectList(
                        new LambdaQueryWrapper<Auction>().in(Auction::getItemId, itemIds))
                .stream()
                .collect(Collectors.toMap(Auction::getItemId, Function.identity(), (a, b) -> a));

        List<Long> schoolIds = items.stream().map(Item::getSchoolId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, String> schoolNameMap = schoolIds.isEmpty() ? Map.of()
                : schoolMapper.selectBatchIds(schoolIds).stream()
                        .collect(Collectors.toMap(School::getId,
                                s -> s.getShortName() == null ? s.getName() : s.getShortName()));

        return items.stream().map(item -> {
            Auction auction = auctionMap.get(item.getId());
            return MyItemVO.builder()
                    .itemId(item.getId())
                    .auctionId(auction != null ? auction.getId() : null)
                    .title(item.getTitle())
                    .schoolName(schoolNameMap.get(item.getSchoolId()))
                    .images(jsonUtil.parseImages(item.getImages()))
                    .itemStatus(item.getStatus())
                    .auctionStatus(auction != null ? auction.getStatus() : null)
                    .rejectReason(item.getRejectReason())
                    .currentPrice(auction != null ? auction.getCurrentPrice() : null)
                    .bidCount(auction != null ? auction.getBidCount() : 0)
                    .endTime(auction != null && auction.getEndTime() != null
                            ? TimeUtil.str(auction.getEndTime()) : null)
                    .build();
        }).toList();
    }
}
