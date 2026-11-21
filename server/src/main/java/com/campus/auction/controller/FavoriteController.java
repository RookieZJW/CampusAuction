package com.campus.auction.controller;

import com.campus.auction.common.Result;
import com.campus.auction.common.ResultCode;
import com.campus.auction.interceptor.AuthInterceptor;
import com.campus.auction.mapper.UserMapper;
import com.campus.auction.service.FavoriteService;
import com.campus.auction.service.SchoolService;
import com.campus.auction.vo.FavoriteVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "收藏")
@RestController
@RequestMapping("/api/v1/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;
    private final SchoolService schoolService;
    private final UserMapper userMapper;

    @Operation(summary = "我的收藏列表")
    @GetMapping("/mine")
    public Result<List<FavoriteVO>> mine(@RequestAttribute(AuthInterceptor.ATTR_OPENID) String openid) {
        Long userId = userMapper.selectIdByOpenid(openid);
        if (userId == null) {
            return Result.fail(ResultCode.UNAUTHORIZED);
        }
        return Result.ok(favoriteService.mine(userId));
    }

    @Operation(summary = "是否已收藏某拍卖", description = "详情页渲染收藏按钮状态用")
    @GetMapping("/auctions/{auctionId}/check")
    public Result<Map<String, Object>> check(
            @RequestAttribute(AuthInterceptor.ATTR_OPENID) String openid,
            @PathVariable Long auctionId) {
        Long userId = userMapper.selectIdByOpenid(openid);
        if (userId == null) {
            return Result.fail(ResultCode.UNAUTHORIZED);
        }
        return Result.ok(favoriteService.check(userId, auctionId));
    }

    @Operation(summary = "收藏/取消收藏（切换）", description = "挂在拍卖上：POST /auctions/{id}/favorite 由本控制器提供转发")
    @PostMapping("/auctions/{auctionId}")
    public Result<Map<String, Object>> toggle(
            @RequestAttribute(AuthInterceptor.ATTR_OPENID) String openid,
            @PathVariable Long auctionId) {
        Long userId = userMapper.selectIdByOpenid(openid);
        if (userId == null) {
            return Result.fail(ResultCode.UNAUTHORIZED);
        }
        schoolService.assertTradable(userId, auctionId);
        return Result.ok(favoriteService.toggle(userId, auctionId));
    }
}
