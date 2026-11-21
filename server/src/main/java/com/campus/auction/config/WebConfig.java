package com.campus.auction.config;

import com.campus.auction.interceptor.AuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置：登录拦截器注册 + 跨域 + 上传静态资源映射
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    @Value("${upload.dir:./uploads}")
    private String uploadDir;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                // 白名单：登录接口 + 接口文档
                .excludePathPatterns(
                        "/api/v1/auth/login",
                        "/api/v1/auth/register",
                        "/api/v1/auth/mock-login",
                        "/api/v1/admin/login",
                        // 未登录可浏览（审核合规：核心浏览功能不强制登录）
                        "/api/v1/categories",
                        // 学校字典公开（认证页选择用）；认证/我的学校接口需登录
                        "/api/v1/schools",
                        "/api/v1/auctions",
                        "/api/v1/auctions/*",
                        "/api/v1/auctions/*/live",
                        // 第三方支付回调：无登录态，验签在网关实现内完成
                        "/api/v1/pay/notify/**",
                        "/doc.html",
                        "/webjars/**",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-resources/**",
                        "/favicon.ico",
                        "/uploads/**",
                        "/error"
                );
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 图片静态资源映射：/uploads/** → 本地 uploads 目录
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadDir + "/");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
