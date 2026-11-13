package com.campus.auction.service.impl;

import com.campus.auction.common.exception.BusinessException;
import com.campus.auction.common.ResultCode;
import com.campus.auction.dto.PriceSuggestDTO;
import com.campus.auction.entity.Category;
import com.campus.auction.mapper.CategoryMapper;
import com.campus.auction.service.AiPriceService;
import com.campus.auction.vo.PriceSuggestVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * AI 定价建议实现：
 * 1. 大模型通道：DeepSeek（OpenAI 兼容 /chat/completions），要求模型返回严格 JSON，解析价格区间；
 * 2. 规则降级通道：未配置 Key、调用超时/失败、返回不合法时，按「分类基准价 × 成色系数」启发式估价。
 * 两个通道对前端透明（source 字段区分），保证估价功能永不 500。
 */
@Slf4j
@Service
public class AiPriceServiceImpl implements AiPriceService {

    /** 分类二手基准价（元）：论文中的规则估价基线 */
    private static final Map<String, Integer> CATEGORY_BASE = Map.of(
            "教材书籍", 25,
            "数码电子", 400,
            "生活用品", 30,
            "运动器材", 120,
            "服饰鞋包", 70,
            "乐器", 300,
            "其他", 50);

    /** 成色折价系数 */
    private static final Map<String, Double> CONDITION_FACTOR = Map.of(
            "全新", 1.0,
            "九成新", 0.8,
            "八成新", 0.65,
            "七成新", 0.5,
            "明显使用痕迹", 0.35);

    private final CategoryMapper categoryMapper;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    @Value("${ai.price.enabled:false}")
    private boolean enabled;

    @Value("${ai.price.api-key:}")
    private String apiKey;

    @Value("${ai.price.base-url:https://api.deepseek.com}")
    private String baseUrl;

    @Value("${ai.price.model:deepseek-chat}")
    private String model;

    public AiPriceServiceImpl(CategoryMapper categoryMapper, ObjectMapper objectMapper) {
        this.categoryMapper = categoryMapper;
        this.objectMapper = objectMapper;
        // 8 秒超时：估价是辅助功能，不能阻塞发布流程太久
        // 注意：baseUrl 是 @Value 注入，构造时还没就绪，调用处再拼完整 URI
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(8));
        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    @Override
    public PriceSuggestVO suggest(PriceSuggestDTO dto) {
        String categoryName = resolveCategoryName(dto.getCategoryId());
        String condition = dto.getCondition() == null ? "八成新" : dto.getCondition();

        if (enabled && apiKey != null && !apiKey.isBlank()) {
            try {
                PriceSuggestVO ai = callLlm(dto, categoryName, condition);
                if (ai != null) {
                    return ai;
                }
            } catch (Exception e) {
                // 任何异常（超时、限流、解析失败）都静默降级，不中断发布
                log.warn("AI 定价调用失败，降级规则估价: {}", e.getMessage());
            }
        }
        return ruleSuggest(dto, categoryName, condition);
    }

    /** 调用大模型，返回 null 表示需要降级 */
    private PriceSuggestVO callLlm(PriceSuggestDTO dto, String categoryName, String condition) {
        String system = "你是二手物品估价专家。根据物品信息给出校园二手交易的合理起拍价区间（人民币，单位元）。"
                + "只返回 JSON，不要任何多余文字，格式：{\"min\":数字,\"max\":数字,\"reason\":\"一句话依据\"}。"
                + "min/max 为 0.5 的整数倍，max 不超过 min 的 3 倍。";

        StringBuilder user = new StringBuilder();
        user.append("物品标题：").append(dto.getTitle()).append('\n');
        user.append("分类：").append(categoryName).append('\n');
        user.append("成色：").append(condition).append('\n');
        if (dto.getDescription() != null && !dto.getDescription().isBlank()) {
            user.append("描述：").append(dto.getDescription());
        }

        Map<String, Object> body = Map.of(
                "model", model,
                "temperature", 0.2,
                "max_tokens", 300,
                "messages", List.of(
                        Map.of("role", "system", "content", system),
                        Map.of("role", "user", "content", user.toString())));

        JsonNode resp = restClient.post()
                .uri(baseUrl + "/chat/completions")
                .headers(h -> {
                    h.setBearerAuth(apiKey);
                    h.setContentType(MediaType.APPLICATION_JSON);
                })
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        String content = resp == null ? null
                : resp.path("choices").path(0).path("message").path("content").asText(null);
        if (content == null || content.isBlank()) {
            return null;
        }
        // 剥掉模型可能自作主张加的 markdown 代码围栏
        String json = content.trim()
                .replaceFirst("^```(json)?", "")
                .replaceFirst("```$", "")
                .trim();

        JsonNode parsed;
        try {
            parsed = objectMapper.readTree(json);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.warn("AI 返回内容非合法 JSON: {}", json);
            return null;
        }
        if (!parsed.has("min") || !parsed.has("max")) {
            return null;
        }
        double min = parsed.get("min").asDouble();
        double max = parsed.get("max").asDouble();
        if (min <= 0 || max <= min || max > min * 5) {
            return null;
        }
        return PriceSuggestVO.builder()
                .minPrice(yuanToFen(min))
                .maxPrice(yuanToFen(max))
                .source("AI")
                .reason(parsed.path("reason").asText("基于同类二手成交行情"))
                .build();
    }

    /** 规则估价：分类基准价 × 成色系数，区间 [0.6, 1.2] 倍 */
    private PriceSuggestVO ruleSuggest(PriceSuggestDTO dto, String categoryName, String condition) {
        int base = CATEGORY_BASE.getOrDefault(categoryName, 50);
        double factor = CONDITION_FACTOR.getOrDefault(condition, 0.65);
        int mid = (int) Math.max(1, Math.round(base * factor));
        int min = Math.max(1, (int) Math.floor(mid * 0.6));
        int max = (int) Math.ceil(mid * 1.2);
        return PriceSuggestVO.builder()
                .minPrice(yuanToFen(min))
                .maxPrice(yuanToFen(max))
                .source("RULE")
                .reason(String.format("按「%s × %s」规则估算（AI 未启用或暂不可用）", categoryName, condition))
                .build();
    }

    private String resolveCategoryName(Long categoryId) {
        Category category = categoryMapper.selectById(categoryId);
        if (category == null) {
            throw new BusinessException(ResultCode.BIZ_ERROR, "分类不存在");
        }
        return category.getName();
    }

    private int yuanToFen(double yuan) {
        // 0.5 元粒度 → 50 分
        return (int) Math.round(yuan * 2) * 50;
    }
}
