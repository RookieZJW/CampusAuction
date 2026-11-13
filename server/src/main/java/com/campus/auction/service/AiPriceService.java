package com.campus.auction.service;

import com.campus.auction.dto.PriceSuggestDTO;
import com.campus.auction.vo.PriceSuggestVO;

/**
 * AI 定价建议：优先调用大模型（DeepSeek 等 OpenAI 兼容接口），
 * 未配置 Key 或调用失败时降级为规则估价（启发式），保证功能永不中断。
 */
public interface AiPriceService {

    PriceSuggestVO suggest(PriceSuggestDTO dto);
}
