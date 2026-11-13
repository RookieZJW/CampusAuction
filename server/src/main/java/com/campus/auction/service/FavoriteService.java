package com.campus.auction.service;

import com.campus.auction.vo.FavoriteVO;

import java.util.List;
import java.util.Map;

/** 收藏 */
public interface FavoriteService {

    /** 切换收藏状态，返回收藏后状态与总数 */
    Map<String, Object> toggle(Long userId, Long auctionId);

    /** 是否已收藏（详情页按钮状态） */
    Map<String, Object> check(Long userId, Long auctionId);

    List<FavoriteVO> mine(Long userId);
}
