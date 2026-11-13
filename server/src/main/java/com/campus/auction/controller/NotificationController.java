package com.campus.auction.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campus.auction.common.Result;
import com.campus.auction.common.ResultCode;
import com.campus.auction.entity.Notification;
import com.campus.auction.interceptor.AuthInterceptor;
import com.campus.auction.mapper.NotificationMapper;
import com.campus.auction.mapper.UserMapper;
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

@Tag(name = "消息")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationMapper notificationMapper;
    private final UserMapper userMapper;

    @Operation(summary = "消息列表", description = "最近 30 条 + 未读数")
    @GetMapping
    public Result<Map<String, Object>> list(@RequestAttribute(AuthInterceptor.ATTR_OPENID) String openid) {
        Long userId = userMapper.selectIdByOpenid(openid);
        if (userId == null) {
            return Result.fail(ResultCode.UNAUTHORIZED);
        }
        List<Notification> list = notificationMapper.selectList(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .orderByDesc(Notification::getCreateTime)
                        .last("LIMIT 30"));
        Long unread = notificationMapper.selectCount(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .eq(Notification::getIsRead, 0));
        return Result.ok(Map.of("list", list, "unread", unread));
    }

    @Operation(summary = "标记已读", description = "传 0 表示全部已读")
    @PostMapping("/{id}/read")
    public Result<Void> read(@RequestAttribute(AuthInterceptor.ATTR_OPENID) String openid,
                             @PathVariable Long id) {
        Long userId = userMapper.selectIdByOpenid(openid);
        if (userId == null) {
            return Result.fail(ResultCode.UNAUTHORIZED);
        }
        notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(id == 0, Notification::getUserId, userId)
                .eq(id != 0, Notification::getId, id)
                .eq(id != 0, Notification::getUserId, userId)
                .set(Notification::getIsRead, 1));
        return Result.ok();
    }
}
