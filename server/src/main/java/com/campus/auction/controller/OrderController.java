package com.campus.auction.controller;

import com.campus.auction.common.Result;
import com.campus.auction.common.ResultCode;
import com.campus.auction.dto.TradePlaceDTO;
import com.campus.auction.interceptor.AuthInterceptor;
import com.campus.auction.mapper.UserMapper;
import com.campus.auction.service.OrderService;
import com.campus.auction.service.PaymentService;
import com.campus.auction.vo.OrderVO;
import com.campus.auction.vo.PayPrepareVO;
import com.campus.auction.vo.PayStatusVO;
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

@Tag(name = "订单")
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final PaymentService paymentService;
    private final UserMapper userMapper;

    @Operation(summary = "我的订单", description = "买入与卖出，含拍品信息与对手方昵称")
    @GetMapping("/mine")
    public Result<List<OrderVO>> mine(@RequestAttribute(AuthInterceptor.ATTR_OPENID) String openid) {
        Long userId = userMapper.selectIdByOpenid(openid);
        if (userId == null) {
            return Result.fail(ResultCode.UNAUTHORIZED);
        }
        return Result.ok(orderService.myOrders(userId));
    }

    @Operation(summary = "确认完成交割", description = "仅买方；完成后解冻保证金、双方信用分+2")
    @PostMapping("/{orderId}/complete")
    public Result<Void> complete(@RequestAttribute(AuthInterceptor.ATTR_OPENID) String openid,
                                 @PathVariable Long orderId) {
        Long userId = userMapper.selectIdByOpenid(openid);
        if (userId == null) {
            return Result.fail(ResultCode.UNAUTHORIZED);
        }
        orderService.completeTrade(orderId, userId);
        return Result.ok();
    }

    @Operation(summary = "约定交割地点", description = "买卖双方均可设置，通知对手方")
    @PostMapping("/{orderId}/place")
    public Result<Void> place(@RequestAttribute(AuthInterceptor.ATTR_OPENID) String openid,
                              @PathVariable Long orderId,
                              @Valid @RequestBody TradePlaceDTO dto) {
        Long userId = userMapper.selectIdByOpenid(openid);
        if (userId == null) {
            return Result.fail(ResultCode.UNAUTHORIZED);
        }
        orderService.updateTradePlace(orderId, userId, dto.getPlace());
        return Result.ok();
    }

    // ------------------------------------------------------------------
    // 支付网关（方案 B）：与交割状态机解耦，模拟支付零合规风险
    // ------------------------------------------------------------------

    @Operation(summary = "发起支付", description = "买方生成支付单，返回收银台参数；PAYING 下重复调用幂等返回")
    @PostMapping("/{orderId}/pay")
    public Result<PayPrepareVO> preparePay(@RequestAttribute(AuthInterceptor.ATTR_OPENID) String openid,
                                           @PathVariable Long orderId) {
        Long userId = requireUserId(openid);
        return Result.ok(paymentService.prepare(orderId, userId));
    }

    @Operation(summary = "查询支付状态", description = "买卖双方均可查询")
    @GetMapping("/{orderId}/pay")
    public Result<PayStatusVO> payStatus(@RequestAttribute(AuthInterceptor.ATTR_OPENID) String openid,
                                         @PathVariable Long orderId) {
        Long userId = requireUserId(openid);
        return Result.ok(paymentService.status(orderId, userId));
    }

    @Operation(summary = "模拟支付成功", description = "演示专用：复用统一回调链路完成验签、幂等与状态流转")
    @PostMapping("/{orderId}/pay/confirm")
    public Result<PayStatusVO> confirmPay(@RequestAttribute(AuthInterceptor.ATTR_OPENID) String openid,
                                          @PathVariable Long orderId) {
        Long userId = requireUserId(openid);
        return Result.ok(paymentService.confirm(orderId, userId));
    }

    @Operation(summary = "关闭支付单", description = "买方取消支付，可重新发起")
    @PostMapping("/{orderId}/pay/close")
    public Result<PayStatusVO> closePay(@RequestAttribute(AuthInterceptor.ATTR_OPENID) String openid,
                                        @PathVariable Long orderId) {
        Long userId = requireUserId(openid);
        return Result.ok(paymentService.close(orderId, userId));
    }

    private Long requireUserId(String openid) {
        Long userId = userMapper.selectIdByOpenid(openid);
        if (userId == null) {
            throw new com.campus.auction.common.exception.BusinessException(ResultCode.UNAUTHORIZED);
        }
        return userId;
    }
}
