package com.campus.auction.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TradePlaceDTO {

    @NotBlank(message = "交割地点不能为空")
    @Size(max = 255, message = "地点最多 255 字")
    private String place;
}
