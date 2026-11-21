package com.campus.auction.controller;

import com.campus.auction.common.Result;
import com.campus.auction.dto.SchoolAuthSubmitDTO;
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

    @Operation(summary = "我的认证状态", description = "状态/学校/姓名学号学生证/驳回理由")
    @GetMapping("/mine")
    public Result<Map<String, Object>> mine(
            @RequestAttribute(AuthInterceptor.ATTR_OPENID) String openid) {
        return Result.ok(schoolService.myAuth(openid));
    }

    @Operation(summary = "提交认证申请", description = "学校+姓名+学号+学生证，提交后管理员审核")
    @PostMapping("/auth")
    public Result<Map<String, Object>> auth(
            @RequestAttribute(AuthInterceptor.ATTR_OPENID) String openid,
            @Valid @RequestBody SchoolAuthSubmitDTO dto) {
        return Result.ok(schoolService.submitAuth(
                openid, dto.getSchoolId(), dto.getRealName(), dto.getStudentNo(), dto.getStudentCard()));
    }
}
