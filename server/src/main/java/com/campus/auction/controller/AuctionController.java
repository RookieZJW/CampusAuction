package com.campus.auction.controller;

import com.campus.auction.common.Result;
import com.campus.auction.common.ResultCode;
import com.campus.auction.service.AuctionService;
import com.campus.auction.vo.AuctionDetailVO;
import com.campus.auction.vo.AuctionListVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "拍卖")
@RestController
@RequestMapping("/api/v1/auctions")
@RequiredArgsConstructor
public class AuctionController {

    private final AuctionService auctionService;

    @Operation(summary = "拍卖大厅列表", description = "分页返回进行中的拍卖，即将结束的在前")
    @GetMapping
    public Result<Map<String, Object>> list(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "10") int size,
                                            @RequestParam(required = false) Long categoryId) {
        return Result.ok(auctionService.pageList(page, size, categoryId));
    }

    @Operation(summary = "拍卖详情", description = "拍品信息 + 出价记录")
    @GetMapping("/{id}")
    public Result<AuctionDetailVO> detail(@PathVariable Long id) {
        AuctionDetailVO vo = auctionService.detail(id);
        if (vo == null) {
            return Result.fail(ResultCode.NOT_FOUND, "拍卖不存在");
        }
        return Result.ok(vo);
    }
}
