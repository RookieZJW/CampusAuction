package com.campus.auction.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 订单互评入参 */
@Data
public class ReviewDTO {

    @NotNull(message = "评分不能为空")
    @Min(value = 1, message = "评分 1-5 星")
    @Max(value = 5, message = "评分 1-5 星")
    private Integer rating;

    @Size(max = 500, message = "评语最长 500 字")
    private String content;
}
