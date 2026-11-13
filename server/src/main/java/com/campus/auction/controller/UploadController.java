package com.campus.auction.controller;

import com.campus.auction.common.Result;
import com.campus.auction.common.ResultCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 图片上传：存本地 uploads 目录（毕设简化方案，生产环境换 OSS/CDN）
 * 上传结果返回可直接访问的完整 URL
 */
@Slf4j
@Tag(name = "上传")
@RestController
@RequestMapping("/api/v1/upload")
public class UploadController {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp");
    private static final long MAX_SIZE = 2 * 1024 * 1024;

    @Value("${upload.dir:./uploads}")
    private String uploadDir;

    @Operation(summary = "上传图片", description = "jpg/png/webp，最大 2MB，返回完整可访问 URL")
    @PostMapping("/image")
    public Result<Map<String, String>> uploadImage(@RequestPart("file") MultipartFile file,
                                                   HttpServletRequest request) throws IOException {
        if (file == null || file.isEmpty()) {
            return Result.fail(ResultCode.PARAM_ERROR, "文件为空");
        }
        if (file.getSize() > MAX_SIZE) {
            return Result.fail(ResultCode.PARAM_ERROR, "图片不能超过 2MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase())) {
            return Result.fail(ResultCode.PARAM_ERROR, "仅支持 jpg/png/webp 格式");
        }

        // 扩展名从 Content-Type 推导，不信任原始文件名（防路径穿越）
        String ext = switch (contentType.toLowerCase()) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> "jpg";
        };
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;

        Path dir = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(dir);
        file.transferTo(dir.resolve(filename).toFile());

        // 用请求 Host 拼完整 URL，本地与部署环境通用
        String baseUrl = "http://" + request.getHeader("Host");
        String url = baseUrl + "/uploads/" + filename;
        log.info("图片上传成功：{} ({} bytes)", url, file.getSize());

        return Result.ok(Map.of("url", url));
    }
}
