package com.campus.auction.service;

import com.campus.auction.entity.Auction;

import java.util.List;
import java.util.Map;

/**
 * 学校认证与多校隔离服务。
 *
 * <p>认证流程：提交申请（姓名/学号/学生证/学校）→ 管理员审核 → 通过后写入 user.school_id。
 * 驳回可修改重交；已认证用户改学校需重新审核（审核期间失去旧学校身份）。
 */
public interface SchoolService {

    /** 启用中的学校字典（id/name/shortName） */
    List<Map<String, Object>> enabledSchools();

    /** 我的认证状态：状态 + 学校 + 姓名学号学生证 + 驳回理由 */
    Map<String, Object> myAuth(String openid);

    /** 提交认证申请（PENDING）；已通过用户改学校会清掉旧 school_id 并重审 */
    Map<String, Object> submitAuth(String openid, Long schoolId, String realName,
                                   String studentNo, String studentCard);

    /** 管理员：待审列表（含学生证照片） */
    List<Map<String, Object>> pendingAuths();

    /** 管理员：审核通过，写入 user.school_id + auth_status=APPROVED */
    void approveAuth(Long authId, Long adminId);

    /** 管理员：驳回，写理由 + auth_status=REJECTED */
    void rejectAuth(Long authId, Long adminId, String reason);

    /** 当前用户资料用：学校 id + 学校名 + 是否已认证 */
    Map<String, Object> mySchool(String openid);

    Long schoolIdOfOpenid(String openid);

    Long schoolIdOfUser(Long userId);

    /** 列表/详情跨校校验：登录用户访问外校抛 3010，游客/未认证放行 */
    void assertReadable(String openid, Long auctionId);

    /** 交易跨校校验：未认证抛 3011，外校抛 3010 */
    void assertTradable(Long userId, Long auctionId);

    boolean belongsToSchool(Auction auction, Long schoolId);
}
