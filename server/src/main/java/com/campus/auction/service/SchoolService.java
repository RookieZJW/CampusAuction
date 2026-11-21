package com.campus.auction.service;

import com.campus.auction.entity.Auction;

import java.util.List;
import java.util.Map;

/**
 * 学校认证与多校隔离服务。
 *
 * <p>规则：
 * <ul>
 *   <li>游客（无 token）：可浏览全部拍品列表与详情，发起交易时仍需登录；</li>
 *   <li>登录用户：列表与详情按本校过滤；未认证用户视为无学校，看全部但交易被引导先认证；</li>
 *   <li>交易（出价/收藏/订单）：登录用户 跨校 一律拒绝。</li>
 * </ul>
 */
public interface SchoolService {

    /** 启用中的学校字典（id/name/shortName），供认证页下拉选择 */
    List<Map<String, Object>> enabledSchools();

    /** 选学校即认证：写入 user.school_id，返回认证结果（含学校名） */
    Map<String, Object> certify(String openid, Long schoolId);

    /** 当前用户资料用：学校 id + 学校名 + 是否已认证 */
    Map<String, Object> mySchool(String openid);

    /** 查用户学校 id，未登录/未认证返回 null */
    Long schoolIdOfOpenid(String openid);

    /** 查用户学校 id，按 userId */
    Long schoolIdOfUser(Long userId);

    /**
     * 列表/详情的跨校校验：登录用户访问外校拍品时抛 CROSS_SCHOOL_DENIED。
     * 游客或未认证用户放行（保持免登录浏览）。
     */
    void assertReadable(String openid, Long auctionId);

    /**
     * 交易类跨校校验：登录用户必须已认证且拍品属于本校，否则抛业务异常。
     * 入参是 userId（交易接口已解析出 userId）。
     */
    void assertTradable(Long userId, Long auctionId);

    /** 判断拍卖是否属于指定学校（供列表过滤复用） */
    boolean belongsToSchool(Auction auction, Long schoolId);
}
