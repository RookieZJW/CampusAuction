package com.campus.auction.service;

import com.campus.auction.dto.AdminLoginDTO;
import com.campus.auction.dto.AuditDTO;
import com.campus.auction.vo.AuditItemVO;

import java.util.List;

public interface AdminService {

    /** 管理员登录，返回 JWT（subject 形如 admin:1） */
    String login(AdminLoginDTO dto);

    /** 待审核列表（status=PENDING 的拍品 + 关联场次） */
    List<AuditItemVO> auditList();

    /** 审核通过：拍品 ON_SALE，拍卖 ONGOING，起止时间从当前时刻起算 */
    void approve(Long itemId);

    /** 审核驳回：拍品 REJECTED + 理由，拍卖 CANCELLED */
    void reject(Long itemId, AuditDTO dto);
}
