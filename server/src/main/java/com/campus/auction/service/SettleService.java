package com.campus.auction.service;

public interface SettleService {

    /**
     * 结算单场拍卖（幂等）：
     * 有出价 → FINISHED + 幂等建单 + 解冻非中标者保证金 + 通知买卖双方
     * 无出价 → FAILED（流拍）
     * 状态先 CAS 到 SETTLING，防止多实例/重复调度重复处理
     */
    void settle(Long auctionId);
}
