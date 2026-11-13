package com.campus.auction.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_credit_log")
public class CreditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Integer changeValue;

    private String reason;

    private Long refId;

    private LocalDateTime createTime;
}
