package com.campus.auction.controller;

import com.campus.auction.common.Result;
import com.campus.auction.dto.AdminLoginDTO;
import com.campus.auction.dto.AuditDTO;
import com.campus.auction.service.AdminService;
import com.campus.auction.vo.AuditItemVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "管理员")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @Operation(summary = "管理员登录")
    @PostMapping("/login")
    public Result<Map<String, String>> login(@Valid @RequestBody AdminLoginDTO dto) {
        String token = adminService.login(dto);
        return Result.ok(Map.of("token", token));
    }

    @Operation(summary = "待审核拍品列表")
    @GetMapping("/audit/items")
    public Result<List<AuditItemVO>> auditList() {
        return Result.ok(adminService.auditList());
    }

    @Operation(summary = "审核通过", description = "拍品上架，拍卖立即开始，时长从当前时刻起算")
    @PostMapping("/audit/items/{itemId}/approve")
    public Result<Void> approve(@PathVariable Long itemId) {
        adminService.approve(itemId);
        return Result.ok();
    }

    @Operation(summary = "审核驳回", description = "必须填写理由，卖家可修改后重新提交")
    @PostMapping("/audit/items/{itemId}/reject")
    public Result<Void> reject(@PathVariable Long itemId, @RequestBody AuditDTO dto) {
        adminService.reject(itemId, dto);
        return Result.ok();
    }
}
