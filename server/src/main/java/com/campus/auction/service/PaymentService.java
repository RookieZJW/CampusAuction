package com.campus.auction.service;

import com.campus.auction.payment.PaymentNotify;
import com.campus.auction.vo.PayPrepareVO;
import com.campus.auction.vo.PayStatusVO;

/**
 * 支付编排服务：与订单履约状态机解耦的资金链路。
 */
public interface PaymentService {

    /** 买方发起支付：生成支付单并返回收银台参数；PAYING 状态下重复调用幂等返回 */
    PayPrepareVO prepare(Long orderId, Long userId);

    /** 演示专用：模拟用户点「确认支付」，内部复用统一回调链路（验签 + 幂等 + CAS） */
    PayStatusVO confirm(Long orderId, Long userId);

    /** 买方关闭支付单（可重新发起） */
    PayStatusVO close(Long orderId, Long userId);

    /** 买卖双方查询支付状态 */
    PayStatusVO status(Long orderId, Long userId);

    /** 统一回调入口：验签 → 按 pay_no 定位 → CAS 置 PAID → 通知双方；重复回调幂等返回 */
    PayStatusVO handleNotify(PaymentNotify notify);
}
