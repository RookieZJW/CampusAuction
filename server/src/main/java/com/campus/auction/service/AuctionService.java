package com.campus.auction.service;

import com.campus.auction.vo.AuctionDetailVO;
import com.campus.auction.vo.AuctionListVO;

import java.util.List;
import java.util.Map;

public interface AuctionService {

    /**
     * 大厅列表：分页 + 分类筛选，只查 ONGOING
     */
    Map<String, Object> pageList(int page, int size, Long categoryId);

    /**
     * 拍卖详情：拍品信息 + 出价记录（倒序）
     */
    AuctionDetailVO detail(Long auctionId);

    /**
     * 竞价实时态（轮询专用）：currentPrice / bidCount / endTime / status
     */
    Map<String, Object> live(Long auctionId);
}
