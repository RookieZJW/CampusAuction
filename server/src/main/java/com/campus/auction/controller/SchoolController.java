package com.campus.auction.controller;

import com.campus.auction.common.Result;
import com.campus.auction.dto.SchoolAuthDTO;
import com.campus.auction.interceptor.AuthInterceptor;
import com.campus.auction.service.SchoolService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "学校认证")
@RestController
@RequestMapping("/api/v1/schools")
@RequiredArgsConstructor
public class SchoolController {

    private final SchoolService schoolService;

    @Operation(summary = "学校字典", description = "启用中的学校列表，供认证页选择")
    @GetMapping
    public Result<List<Map<String, Object>>> list() {
        return Result.ok(schoolService.enabledSchools());
    }

    @Operation(summary = "我的学校", description = "当前用户是否已认证及学校信息")
    @GetMapping("/mine")
    public Result<Map<String, Object>> mine(
            @RequestAttribute(AuthInterceptor.ATTR_OPENID) String openid) {
        return Result.ok(schoolService.mySchool(openid));
    }

    @Operation(summary = "学校认证", description = "选学校即认证：写入 user.school_id")
    @PostMapping("/auth")
    public Result<Map<String, Object>> auth(
            @RequestAttribute(AuthInterceptor.ATTR_OPENID) String openid,
            @Valid @RequestBody SchoolAuthDTO dto) {
        return Result.ok(schoolService.certify(openid, dto.getSchoolId()));
    }
}
