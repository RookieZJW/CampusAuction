package com.campus.auction.service;

import com.campus.auction.dto.BidDTO;
import com.campus.auction.vo.BidVO;
import com.campus.auction.vo.MyBidVO;

import java.util.List;

public interface BidService {

    /**
     * 出价：Redis Lua 原子预校验（性能层）→ MySQL 乐观锁（正确性层）
     * 含：加价幅度校验、自拍拦截、信用分门槛、保证金冻结、防狙击延时
     */
    BidVO bid(Long userId, Long auctionId, BidDTO dto);

    /**
     * 我的出价（按时间倒序，附带拍卖最新状态）
     */
    List<MyBidVO> myBids(Long userId);
}
