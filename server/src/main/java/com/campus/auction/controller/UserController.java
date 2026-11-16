package com.campus.auction.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.auction.common.Result;
import com.campus.auction.common.ResultCode;
import com.campus.auction.dto.PhoneBindDTO;
import com.campus.auction.dto.ProfileUpdateDTO;
import com.campus.auction.entity.AccountLog;
import com.campus.auction.entity.CreditLog;
import com.campus.auction.entity.User;
import com.campus.auction.interceptor.AuthInterceptor;
import com.campus.auction.mapper.AccountLogMapper;
import com.campus.auction.mapper.CreditLogMapper;
import com.campus.auction.mapper.UserMapper;
import com.campus.auction.service.WxApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Slf4j
@Tag(name = "用户中心")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserMapper userMapper;
    private final AccountLogMapper accountLogMapper;
    private final CreditLogMapper creditLogMapper;
    private final WxApiService wxApiService;

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

    @Operation(summary = "我的资料", description = "头像、昵称、手机号")
    @GetMapping("/profile")
    public Result<Map<String, Object>> profile(
            @RequestAttribute(AuthInterceptor.ATTR_OPENID) String openid) {
        return Result.ok(profileMap(requireUser(openid)));
    }

    @Operation(summary = "更新头像昵称", description = "来自头像昵称填写能力，由用户主动选填")
    @PutMapping("/profile")
    public Result<Map<String, Object>> updateProfile(
            @RequestAttribute(AuthInterceptor.ATTR_OPENID) String openid,
            @Valid @RequestBody ProfileUpdateDTO dto) {
        String nickname = dto.getNickname() == null ? null : dto.getNickname().trim();
        String avatar = dto.getAvatar() == null ? null : dto.getAvatar().trim();
        if ((nickname == null || nickname.isEmpty()) && (avatar == null || avatar.isEmpty())) {
            return Result.fail(ResultCode.PARAM_ERROR, "昵称和头像不能都为空");
        }
        User user = requireUser(openid);
        if (nickname != null && !nickname.isEmpty()) {
            user.setNickname(nickname);
        }
        if (avatar != null && !avatar.isEmpty()) {
            user.setAvatar(avatar);
        }
        userMapper.updateById(user);
        return Result.ok(profileMap(user));
    }

    @Operation(summary = "绑定手机号", description = "用 getPhoneNumber 动态 code 在服务端换取手机号")
    @PostMapping("/phone")
    public Result<Map<String, Object>> bindPhone(
            @RequestAttribute(AuthInterceptor.ATTR_OPENID) String openid,
            @Valid @RequestBody PhoneBindDTO dto) {
        String phone = wxApiService.getPhoneNumber(dto.getCode().trim());
        User user = requireUser(openid);
        user.setPhone(phone);
        userMapper.updateById(user);
        // 只记用户 id，手机号明文不落日志
        log.info("用户 {} 已绑定手机号", user.getId());
        return Result.ok(profileMap(user));
    }

    /** 只回本人的资料；Map.of 不接受 null，空值统一转空串 */
    private Map<String, Object> profileMap(User user) {
        return Map.of(
                "nickname", user.getNickname() == null ? "" : user.getNickname(),
                "avatar", user.getAvatar() == null ? "" : user.getAvatar(),
                "phone", user.getPhone() == null ? "" : user.getPhone());
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
