package com.campus.auction.controller;

import com.campus.auction.common.Result;
import com.campus.auction.common.ResultCode;
import com.campus.auction.dto.ReviewDTO;
import com.campus.auction.interceptor.AuthInterceptor;
import com.campus.auction.mapper.UserMapper;
import com.campus.auction.service.ReviewService;
import com.campus.auction.vo.ReviewVO;
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

@Tag(name = "互评")
@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;
    private final UserMapper userMapper;

    @Operation(summary = "提交互评", description = "订单 COMPLETED 后，买卖双方互评（1-5 星 + 评语），一单一人一次")
    @PostMapping("/orders/{orderId}")
    public Result<Void> submit(@RequestAttribute(AuthInterceptor.ATTR_OPENID) String openid,
                               @PathVariable Long orderId,
                               @Valid @RequestBody ReviewDTO dto) {
        Long userId = userMapper.selectIdByOpenid(openid);
        if (userId == null) {
            return Result.fail(ResultCode.UNAUTHORIZED);
        }
        reviewService.submit(orderId, userId, dto);
        return Result.ok();
    }

    @Operation(summary = "我收到的评价")
    @GetMapping("/received")
    public Result<List<ReviewVO>> received(@RequestAttribute(AuthInterceptor.ATTR_OPENID) String openid) {
        Long userId = userMapper.selectIdByOpenid(openid);
        if (userId == null) {
            return Result.fail(ResultCode.UNAUTHORIZED);
        }
        return Result.ok(reviewService.received(userId));
    }

    @Operation(summary = "我发出的评价")
    @GetMapping("/sent")
    public Result<List<ReviewVO>> sent(@RequestAttribute(AuthInterceptor.ATTR_OPENID) String openid) {
        Long userId = userMapper.selectIdByOpenid(openid);
        if (userId == null) {
            return Result.fail(ResultCode.UNAUTHORIZED);
        }
        return Result.ok(reviewService.sent(userId));
    }
}
