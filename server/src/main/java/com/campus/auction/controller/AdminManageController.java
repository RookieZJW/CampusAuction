package com.campus.auction.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.auction.common.Result;
import com.campus.auction.common.ResultCode;
import com.campus.auction.dto.ReportHandleDTO;
import com.campus.auction.entity.Auction;
import com.campus.auction.entity.Category;
import com.campus.auction.entity.Item;
import com.campus.auction.entity.Order;
import com.campus.auction.entity.User;
import com.campus.auction.interceptor.AuthInterceptor;
import com.campus.auction.mapper.AuctionMapper;
import com.campus.auction.mapper.CategoryMapper;
import com.campus.auction.mapper.ItemMapper;
import com.campus.auction.mapper.OrderMapper;
import com.campus.auction.mapper.UserMapper;
import com.campus.auction.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理后台扩展接口（Web 端 Vue 后台使用，均为管理员鉴权）
 */
@Tag(name = "管理后台")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminManageController {

    private final UserMapper userMapper;
    private final AuctionMapper auctionMapper;
    private final ItemMapper itemMapper;
    private final OrderMapper orderMapper;
    private final CategoryMapper categoryMapper;
    private final ReportService reportService;

    @Operation(summary = "数据看板")
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        long totalUsers = userMapper.selectCount(null);
        long totalItems = itemMapper.selectCount(null);
        long pendingItems = itemMapper.selectCount(
                new LambdaQueryWrapper<Item>().eq(Item::getStatus, Item.STATUS_PENDING));
        long ongoing = auctionMapper.selectCount(
                new LambdaQueryWrapper<Auction>().eq(Auction::getStatus, Auction.STATUS_ONGOING));
        long finished = auctionMapper.selectCount(
                new LambdaQueryWrapper<Auction>().eq(Auction::getStatus, Auction.STATUS_FINISHED));
        long failed = auctionMapper.selectCount(
                new LambdaQueryWrapper<Auction>().eq(Auction::getStatus, Auction.STATUS_FAILED));

        List<Order> completed = orderMapper.selectList(
                new LambdaQueryWrapper<Order>().eq(Order::getStatus, Order.STATUS_COMPLETED));
        int dealAmount = completed.stream().mapToInt(o -> o.getDealPrice() == null ? 0 : o.getDealPrice()).sum();

        // 类目成交分布（用真实类目名，便于后台看板展示）
        Map<String, Integer> categoryStats = new HashMap<>();
        if (!completed.isEmpty()) {
            List<Long> itemIds = completed.stream().map(Order::getItemId).distinct().toList();
            Map<Long, Long> itemCategory = itemMapper.selectBatchIds(itemIds).stream()
                    .collect(java.util.stream.Collectors.toMap(Item::getId, Item::getCategoryId));
            Map<Long, String> categoryNames = categoryMapper.selectList(null).stream()
                    .collect(java.util.stream.Collectors.toMap(Category::getId, Category::getName));
            completed.forEach(o -> {
                Long categoryId = itemCategory.get(o.getItemId());
                if (categoryId != null) {
                    String name = categoryNames.getOrDefault(categoryId, "未分类");
                    categoryStats.merge(name, 1, Integer::sum);
                }
            });
        }

        return Result.ok(Map.of(
                "totalUsers", totalUsers,
                "totalItems", totalItems,
                "pendingItems", pendingItems,
                "ongoing", ongoing,
                "finished", finished,
                "failed", failed,
                "completedOrders", completed.size(),
                "dealAmount", dealAmount,
                "dealAmountYuan", String.format("%.2f", dealAmount / 100.0),
                "categoryStats", categoryStats
        ));
    }

    @Operation(summary = "用户管理列表", description = "支持昵称模糊搜索与分页")
    @GetMapping("/users")
    public Result<Map<String, Object>> users(@RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "10") int size,
                                             @RequestParam(required = false) String keyword) {
        Page<User> result = userMapper.selectPage(
                new Page<>(page, Math.min(size, 50)),
                new LambdaQueryWrapper<User>()
                        .like(keyword != null && !keyword.isBlank(), User::getNickname, keyword)
                        .orderByDesc(User::getCreateTime));
        return Result.ok(Map.of(
                "list", result.getRecords(),
                "total", result.getTotal()));
    }

    @Operation(summary = "封禁用户", description = "封禁后无法登录与出价")
    @PostMapping("/users/{userId}/ban")
    public Result<Void> ban(@PathVariable Long userId) {
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getStatus, 1));
        return Result.ok();
    }

    @Operation(summary = "解封用户")
    @PostMapping("/users/{userId}/unban")
    public Result<Void> unban(@PathVariable Long userId) {
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getStatus, 0));
        return Result.ok();
    }

    @Operation(summary = "拍卖监控列表", description = "按状态过滤，含拍品标题")
    @GetMapping("/auctions")
    public Result<Map<String, Object>> auctions(@RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "10") int size,
                                                @RequestParam(required = false) String status) {
        Page<Auction> result = auctionMapper.selectPage(
                new Page<>(page, Math.min(size, 50)),
                new LambdaQueryWrapper<Auction>()
                        .eq(status != null && !status.isBlank(), Auction::getStatus, status)
                        .orderByDesc(Auction::getCreateTime));
        List<Long> itemIds = result.getRecords().stream().map(Auction::getItemId).distinct().toList();
        Map<Long, String> titleMap = itemIds.isEmpty() ? Map.of()
                : itemMapper.selectBatchIds(itemIds).stream()
                        .collect(java.util.stream.Collectors.toMap(Item::getId, Item::getTitle));
        List<Map<String, Object>> list = result.getRecords().stream().map(a -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", a.getId());
            m.put("itemId", a.getItemId());
            m.put("title", titleMap.getOrDefault(a.getItemId(), "已删除"));
            m.put("status", a.getStatus());
            m.put("currentPrice", a.getCurrentPrice());
            m.put("bidCount", a.getBidCount());
            m.put("startTime", a.getStartTime());
            m.put("endTime", a.getEndTime());
            m.put("extendCount", a.getExtendCount());
            return m;
        }).toList();
        return Result.ok(Map.of("list", list, "total", result.getTotal()));
    }

    @Operation(summary = "订单列表")
    @GetMapping("/orders")
    public Result<Map<String, Object>> orders(@RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "10") int size,
                                              @RequestParam(required = false) String status) {
        Page<Order> result = orderMapper.selectPage(
                new Page<>(page, Math.min(size, 50)),
                new LambdaQueryWrapper<Order>()
                        .eq(status != null && !status.isBlank(), Order::getStatus, status)
                        .orderByDesc(Order::getCreateTime));
        return Result.ok(Map.of(
                "list", result.getRecords(),
                "total", result.getTotal()));
    }

    // ---------- 举报处理（W5） ----------

    @Operation(summary = "举报列表", description = "按状态过滤，先到先处理")
    @GetMapping("/reports")
    public Result<Map<String, Object>> reports(@RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "10") int size,
                                               @RequestParam(required = false) String status) {
        return Result.ok(reportService.adminList(page, size, status));
    }

    @Operation(summary = "处理举报", description = "action=RESOLVED 核实（被举报方信用 -20 + 通知）/ DISMISSED 驳回；双方均收到处理结果通知")
    @PostMapping("/reports/{reportId}/handle")
    public Result<Void> handleReport(@RequestAttribute(AuthInterceptor.ATTR_ADMIN_ID) Long adminId,
                                     @PathVariable Long reportId,
                                     @Valid @RequestBody ReportHandleDTO dto) {
        reportService.handle(reportId, adminId, dto);
        return Result.ok();
    }
}
