package com.campus.auction.controller;

import com.campus.auction.common.Result;
import com.campus.auction.common.ResultCode;
import com.campus.auction.dto.BidDTO;
import com.campus.auction.interceptor.AuthInterceptor;
import com.campus.auction.mapper.UserMapper;
import com.campus.auction.service.BidService;
import com.campus.auction.vo.BidVO;
import com.campus.auction.vo.MyBidVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "竞价")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class BidController {

    private final BidService bidService;
    private final UserMapper userMapper;

    @Operation(summary = "出价", description = "双层校验：Redis Lua 原子预校验 + MySQL 乐观锁；含防狙击延时与保证金冻结")
    @PostMapping("/auctions/{auctionId}/bids")
    public Result<BidVO> bid(@RequestAttribute(AuthInterceptor.ATTR_OPENID) String openid,
                             @PathVariable Long auctionId,
                             @Valid @RequestBody BidDTO dto) {
        Long userId = userMapper.selectIdByOpenid(openid);
        if (userId == null) {
            return Result.fail(ResultCode.UNAUTHORIZED);
        }
        return Result.ok(bidService.bid(userId, auctionId, dto));
    }

    @Operation(summary = "我的出价")
    @GetMapping("/bids/mine")
    public Result<List<MyBidVO>> mine(@RequestAttribute(AuthInterceptor.ATTR_OPENID) String openid) {
        Long userId = userMapper.selectIdByOpenid(openid);
        if (userId == null) {
            return Result.fail(ResultCode.UNAUTHORIZED);
        }
        return Result.ok(bidService.myBids(userId));
    }
}
