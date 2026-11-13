package com.campus.auction.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;

/**
 * Redis 与 RestTemplate 配置
 */
@Configuration
public class RedisConfig {

    /**
     * RedisTemplate：key 用 String 序列化，value 用 JSON 序列化
     * （默认 JdkSerialization 序列化在 redis-cli 里肉眼不可读，不利于调试）
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        ObjectMapper om = new ObjectMapper();
        om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        om.activateDefaultTyping(LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);

        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setValueSerializer(new org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer<>(Object.class));
        template.setHashValueSerializer(new org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer<>(Object.class));
        template.afterPropertiesSet();
        return template;
    }

    /**
     * 调微信 code2session 接口用的 RestTemplate。
     * 微信 API 返回 JSON 但 Content-Type 是 text/plain，
     * 需给 Jackson 转换器追加 text/plain 支持，否则 UnknownContentTypeException。
     */
    @Bean
    public RestTemplate restTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        MappingJackson2HttpMessageConverter jsonConverter =
                new MappingJackson2HttpMessageConverter();
        jsonConverter.setSupportedMediaTypes(java.util.List.of(
                org.springframework.http.MediaType.APPLICATION_JSON,
                org.springframework.http.MediaType.TEXT_PLAIN,
                org.springframework.http.MediaType.APPLICATION_OCTET_STREAM));
        // 放到队首，优先于默认的 Jackson 转换器生效
        restTemplate.getMessageConverters().add(0, jsonConverter);
        return restTemplate;
    }
}
