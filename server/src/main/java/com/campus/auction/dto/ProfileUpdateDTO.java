package com.campus.auction.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 完善资料入参：头像与昵称。
 *
 * <p>微信已收回 wx.getUserProfile / wx.getUserInfo 的真实资料能力，
 * 这里的数据来自「头像昵称填写能力」——由用户主动选择 / 输入，
 * 前端用 chooseAvatar 与 input[type=nickname] 采集后提交。
 * 两个字段都允许为 null，表示只更新其中一项。
 */
@Data
public class ProfileUpdateDTO {

    @Size(max = 32, message = "昵称最多 32 字")
    private String nickname;

    /** 由 /api/v1/upload/image 上传后返回的完整 URL */
    @Size(max = 512, message = "头像地址过长")
    private String avatar;
}
