package com.campus.auction.service;

import com.campus.auction.dto.PublishItemDTO;
import com.campus.auction.vo.MyItemVO;

import java.util.List;

public interface ItemService {

    /**
     * 发布拍品：创建物品(PENDING) + 拍卖场次(PENDING)，等待管理员审核
     */
    Long publish(Long userId, PublishItemDTO dto);

    /**
     * 我发布的拍品列表（含拍卖状态与驳回理由）
     */
    List<MyItemVO> myItems(Long userId);
}
