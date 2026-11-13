package com.campus.auction.controller;

import com.campus.auction.common.Result;
import com.campus.auction.common.ResultCode;
import com.campus.auction.dto.PriceSuggestDTO;
import com.campus.auction.dto.PublishItemDTO;
import com.campus.auction.interceptor.AuthInterceptor;
import com.campus.auction.mapper.UserMapper;
import com.campus.auction.service.AiPriceService;
import com.campus.auction.service.ItemService;
import com.campus.auction.vo.MyItemVO;
import com.campus.auction.vo.PriceSuggestVO;
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

@Tag(name = "拍品")
@RestController
@RequestMapping("/api/v1/items")
@RequiredArgsConstructor
public class ItemController {

    private final ItemService itemService;
    private final AiPriceService aiPriceService;
    private final UserMapper userMapper;

    @Operation(summary = "AI 定价建议", description = "大模型估价（DeepSeek 等），未配 Key 或失败时自动降级规则估价；source 字段区分来源")
    @PostMapping("/price-suggest")
    public Result<PriceSuggestVO> priceSuggest(
            @RequestAttribute(AuthInterceptor.ATTR_OPENID) String openid,
            @Valid @RequestBody PriceSuggestDTO dto) {
        Long userId = userMapper.selectIdByOpenid(openid);
        if (userId == null) {
            return Result.fail(ResultCode.UNAUTHORIZED);
        }
        return Result.ok(aiPriceService.suggest(dto));
    }

    @Operation(summary = "发布拍品", description = "创建物品与拍卖场次，进入待审核状态")
    @PostMapping
    public Result<Long> publish(@RequestAttribute(AuthInterceptor.ATTR_OPENID) String openid,
                                @Valid @RequestBody PublishItemDTO dto) {
        Long userId = userMapper.selectIdByOpenid(openid);
        if (userId == null) {
            return Result.fail(ResultCode.UNAUTHORIZED);
        }
        return Result.ok(itemService.publish(userId, dto));
    }

    @Operation(summary = "我发布的拍品")
    @GetMapping("/mine")
    public Result<List<MyItemVO>> mine(@RequestAttribute(AuthInterceptor.ATTR_OPENID) String openid) {
        Long userId = userMapper.selectIdByOpenid(openid);
        if (userId == null) {
            return Result.fail(ResultCode.UNAUTHORIZED);
        }
        return Result.ok(itemService.myItems(userId));
    }
}
