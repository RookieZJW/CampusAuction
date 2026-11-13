package com.campus.auction.common.exception;

import com.campus.auction.common.ResultCode;
import lombok.Getter;

/**
 * 业务异常：Service 层校验不通过时抛出，由全局异常处理器转成统一响应
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMsg());
        this.code = resultCode.getCode();
    }

    public BusinessException(ResultCode resultCode, String msg) {
        super(msg);
        this.code = resultCode.getCode();
    }
}
