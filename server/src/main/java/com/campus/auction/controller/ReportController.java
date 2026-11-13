package com.campus.auction.controller;

import com.campus.auction.common.Result;
import com.campus.auction.common.ResultCode;
import com.campus.auction.dto.ReportDTO;
import com.campus.auction.interceptor.AuthInterceptor;
import com.campus.auction.mapper.UserMapper;
import com.campus.auction.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "举报")
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final UserMapper userMapper;

    @Operation(summary = "提交举报", description = "举报拍品或用户，同一对象重复举报幂等拒绝")
    @PostMapping
    public Result<Void> submit(@RequestAttribute(AuthInterceptor.ATTR_OPENID) String openid,
                               @Valid @RequestBody ReportDTO dto) {
        Long userId = userMapper.selectIdByOpenid(openid);
        if (userId == null) {
            return Result.fail(ResultCode.UNAUTHORIZED);
        }
        reportService.submit(userId, dto);
        return Result.ok();
    }
}
