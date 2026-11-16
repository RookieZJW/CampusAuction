package com.campus.auction.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.auction.common.ResultCode;
import com.campus.auction.common.exception.BusinessException;
import com.campus.auction.dto.AdminLoginDTO;
import com.campus.auction.dto.AuditDTO;
import com.campus.auction.entity.Admin;
import com.campus.auction.entity.Auction;
import com.campus.auction.entity.Item;
import com.campus.auction.entity.User;
import com.campus.auction.mapper.AdminMapper;
import com.campus.auction.mapper.AuctionMapper;
import com.campus.auction.mapper.CategoryMapper;
import com.campus.auction.mapper.ItemMapper;
import com.campus.auction.mapper.UserMapper;
import com.campus.auction.service.AdminService;
import com.campus.auction.util.JwtUtil;
import com.campus.auction.util.JsonUtil;
import com.campus.auction.vo.AuditItemVO;import com.campus.auction.util.TimeUtil;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final AdminMapper adminMapper;
    private final ItemMapper itemMapper;
    private final AuctionMapper auctionMapper;
    private final UserMapper userMapper;
    private final CategoryMapper categoryMapper;
    private final JsonUtil jsonUtil;
    private final JwtUtil jwtUtil;

    @Override
    public String login(AdminLoginDTO dto) {
        Admin admin = adminMapper.selectOne(
                new LambdaQueryWrapper<Admin>().eq(Admin::getUsername, dto.getUsername()));

        // 密码以 SHA-256 比对（与 init.sql 中 SHA2('admin123',256) 对应）
        String inputHash = sha256(dto.getPassword());
        if (admin == null || !admin.getPasswordHash().equalsIgnoreCase(inputHash)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户名或密码错误");
        }
        if (admin.getStatus() != null && admin.getStatus() == 1) {
            throw new BusinessException(ResultCode.FORBIDDEN, "管理员账号已禁用");
        }
        // 管理员 token 的 subject 加 admin: 前缀，拦截器据此区分身份
        return jwtUtil.generateToken("admin:" + admin.getId());
    }

    @Override
    public List<AuditItemVO> auditList() {
        List<Item> items = itemMapper.selectList(
                new LambdaQueryWrapper<Item>()
                        .eq(Item::getStatus, Item.STATUS_PENDING)
                        .orderByAsc(Item::getCreateTime));
        if (items.isEmpty()) {
            return List.of();
        }

        List<Long> itemIds = items.stream().map(Item::getId).toList();
        Map<Long, Auction> auctionMap = auctionMapper.selectList(
                        new LambdaQueryWrapper<Auction>().in(Auction::getItemId, itemIds))
                .stream()
                .collect(Collectors.toMap(Auction::getItemId, Function.identity(), (a, b) -> a));

        List<Long> sellerIds = items.stream().map(Item::getSellerId).distinct().toList();
        Map<Long, String> nicknameMap = sellerIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(sellerIds).stream()
                        .collect(Collectors.toMap(User::getId,
                                u -> u.getNickname() == null ? "微信用户" : u.getNickname()));

        List<Long> categoryIds = items.stream().map(Item::getCategoryId).distinct().toList();
        Map<Long, String> categoryNameMap = categoryIds.isEmpty() ? Map.of()
                : categoryMapper.selectBatchIds(categoryIds).stream()
                        .collect(Collectors.toMap(com.campus.auction.entity.Category::getId,
                                com.campus.auction.entity.Category::getName));

        return items.stream().map(item -> {
            Auction a = auctionMap.get(item.getId());
            return AuditItemVO.builder()
                    .itemId(item.getId())
                    .sellerId(item.getSellerId())
                    .sellerNickname(nicknameMap.getOrDefault(item.getSellerId(), "未知"))
                    .title(item.getTitle())
                    .description(item.getDescription())
                    .images(jsonUtil.parseImages(item.getImages()))
                    .categoryName(categoryNameMap.getOrDefault(item.getCategoryId(), "其他"))
                    .condition(item.getCondition())
                    .startPrice(a != null ? a.getStartPrice() : null)
                    .increment(a != null ? a.getIncrement() : null)
                    .deposit(a != null ? a.getDeposit() : null)
                    .durationHours(a != null && a.getDurationMinutes() != null
                            ? a.getDurationMinutes() / 60 : null)
                    .submitTime(TimeUtil.str(item.getCreateTime()))
                    .build();
        }).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long itemId) {
        Item item = loadPendingItem(itemId);
        Auction auction = loadAuction(itemId);

        LocalDateTime now = LocalDateTime.now();
        // 审核通过即开拍：起止时间从当前时刻起算
        item.setStatus(Item.STATUS_ON_SALE);
        item.setUpdateTime(now);
        itemMapper.updateById(item);

        auction.setStatus(Auction.STATUS_ONGOING);
        auction.setStartTime(now);
        auction.setEndTime(now.plusMinutes(auction.getDurationMinutes()));
        auction.setOriginalEndTime(auction.getEndTime());
        auction.setCurrentPrice(auction.getStartPrice());
        auction.setUpdateTime(now);
        auctionMapper.updateById(auction);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long itemId, AuditDTO dto) {
        if (dto.getReason() == null || dto.getReason().isBlank()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "驳回必须填写理由");
        }
        Item item = loadPendingItem(itemId);
        Auction auction = loadAuction(itemId);

        item.setStatus(Item.STATUS_REJECTED);
        item.setRejectReason(dto.getReason());
        item.setUpdateTime(LocalDateTime.now());
        itemMapper.updateById(item);

        auction.setStatus(Auction.STATUS_CANCELLED);
        auction.setUpdateTime(LocalDateTime.now());
        auctionMapper.updateById(auction);
    }

    private Item loadPendingItem(Long itemId) {
        Item item = itemMapper.selectById(itemId);
        if (item == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "拍品不存在");
        }
        if (!Item.STATUS_PENDING.equals(item.getStatus())) {
            throw new BusinessException(ResultCode.BIZ_ERROR, "该拍品不在待审核状态");
        }
        return item;
    }

    private Auction loadAuction(Long itemId) {
        Auction auction = auctionMapper.selectOne(
                new LambdaQueryWrapper<Auction>().eq(Auction::getItemId, itemId));
        if (auction == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "拍卖场次不存在");
        }
        return auction;
    }

    /** SHA-256 摘要（小写十六进制），与 MySQL SHA2(str,256) 结果一致 */
    private String sha256(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
