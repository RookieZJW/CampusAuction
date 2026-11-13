package com.campus.auction.service;

import com.campus.auction.dto.ReportDTO;
import com.campus.auction.dto.ReportHandleDTO;
import com.campus.auction.vo.ReportVO;

import java.util.List;
import java.util.Map;

/** 举报提交与后台处理 */
public interface ReportService {

    void submit(Long reporterId, ReportDTO dto);

    /** 管理端列表：status 过滤 + 分页 */
    Map<String, Object> adminList(int page, int size, String status);

    /** 管理员处理：RESOLVED 核实（扣被举报用户信用 -20 + 通知）/ DISMISSED 驳回 */
    void handle(Long reportId, Long adminId, ReportHandleDTO dto);
}
