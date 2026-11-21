package com.campus.auction.common;

import lombok.Getter;

/**
 * 统一响应码定义
 * 0 成功；1000x 参数/客户端错误；2000x 鉴权错误；3000x 业务异常
 */
@Getter
public enum ResultCode {

    SUCCESS(0, "success"),

    PARAM_ERROR(1001, "参数错误"),
    NOT_FOUND(1004, "资源不存在"),

    UNAUTHORIZED(2001, "未登录或登录已过期"),
    TOKEN_INVALID(2002, "无效的登录凭证"),
    FORBIDDEN(2003, "无权访问"),

    BIZ_ERROR(3001, "业务处理失败"),
    AUCTION_NOT_ONGOING(3002, "拍卖已结束或未开始"),
    BID_TOO_LOW(3003, "出价须不低于当前价加加价幅度"),
    SELF_OUTBID(3004, "您已是当前最高出价者"),
    BID_CONFLICT(3005, "出价冲突，请刷新后重试"),
    INSUFFICIENT_BALANCE(3006, "余额不足以支付保证金"),
    LOW_CREDIT(3007, "信用分不足，无法参与竞拍"),
    WX_LOGIN_FAILED(3008, "微信登录失败"),
    SELLER_CANNOT_BID(3009, "卖家不能参与自己发布的拍卖"),
    CROSS_SCHOOL_DENIED(3010, "该拍品不属于你的学校，无法查看或参与"),
    SCHOOL_NOT_AUTHED(3011, "请先完成学校认证"),

    SYSTEM_ERROR(5000, "系统繁忙，请稍后重试");

    private final int code;
    private final String msg;

    ResultCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}
