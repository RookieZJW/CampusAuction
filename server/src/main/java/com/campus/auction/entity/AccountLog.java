package com.campus.auction.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 虚拟余额流水
 * type: GIFT赠送 FREEZE冻结 UNFREEZE解冻 DEDUCT扣除 REFUND退还
 */
@Data
@TableName("t_account_log")
public class AccountLog {

    public static final String TYPE_GIFT = "GIFT";
    public static final String TYPE_FREEZE = "FREEZE";
    public static final String TYPE_UNFREEZE = "UNFREEZE";
    public static final String TYPE_DEDUCT = "DEDUCT";

    /** 关联业务类型：AUCTION / ORDER */
    public static final String REF_AUCTION = "AUCTION";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String type;

    /** 变动金额（分）：冻结为负、解冻为正 */
    private Integer amount;

    /** 变动后余额（对账用） */
    private Integer balanceAfter;

    private String refType;

    private Long refId;

    private String remark;

    private LocalDateTime createTime;
}
