package com.campus.auction.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.auction.common.Result;
import com.campus.auction.common.ResultCode;
import com.campus.auction.entity.AccountLog;
import com.campus.auction.entity.CreditLog;
import com.campus.auction.entity.User;
import com.campus.auction.interceptor.AuthInterceptor;
import com.campus.auction.mapper.AccountLogMapper;
import com.campus.auction.mapper.CreditLogMapper;
import com.campus.auction.mapper.UserMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "用户中心")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserMapper userMapper;
    private final AccountLogMapper accountLogMapper;
    private final CreditLogMapper creditLogMapper;

    @Operation(summary = "钱包", description = "当前余额 + 最近 50 条流水")
    @GetMapping("/wallet")
    public Result<Map<String, Object>> wallet(@RequestAttribute(AuthInterceptor.ATTR_OPENID) String openid) {
        User user = requireUser(openid);
        List<AccountLog> logs = accountLogMapper.selectList(
                new LambdaQueryWrapper<AccountLog>()
                        .eq(AccountLog::getUserId, user.getId())
                        .orderByDesc(AccountLog::getCreateTime)
                        .last("LIMIT 50"));
        return Result.ok(Map.of(
                "balance", user.getBalance(),
                "logs", logs));
    }

    @Operation(summary = "信用分", description = "当前分数 + 最近 50 条明细")
    @GetMapping("/credit")
    public Result<Map<String, Object>> credit(@RequestAttribute(AuthInterceptor.ATTR_OPENID) String openid) {
        User user = requireUser(openid);
        List<CreditLog> logs = creditLogMapper.selectList(
                new LambdaQueryWrapper<CreditLog>()
                        .eq(CreditLog::getUserId, user.getId())
                        .orderByDesc(CreditLog::getCreateTime)
                        .last("LIMIT 50"));
        return Result.ok(Map.of(
                "creditScore", user.getCreditScore(),
                "logs", logs));
    }

    private User requireUser(String openid) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getOpenid, openid));
        if (user == null) {
            throw new com.campus.auction.common.exception.BusinessException(ResultCode.UNAUTHORIZED);
        }
        return user;
    }
}
