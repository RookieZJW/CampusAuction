package com.campus.auction.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 出价入参。金额单位：分
 */
@Data
public class BidDTO {

    @NotNull(message = "出价金额不能为空")
    @Min(value = 100, message = "出价至少 1 元")
    @Max(value = 10000000, message = "出价金额过高")
    private Integer price;
}
