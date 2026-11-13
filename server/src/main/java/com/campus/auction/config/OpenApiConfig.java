package com.campus.auction.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 接口文档配置（knife4j / OpenAPI3）
 * 访问地址：http://localhost:8080/doc.html
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI campusAuctionOpenApi() {
        return new OpenAPI().info(new Info()
                .title("校园在线拍卖系统 API")
                .description("毕业设计：基于微信小程序的校园在线拍卖系统服务端接口")
                .version("v0.1"));
    }
}
