package com.campus.auction.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.auction.common.ResultCode;
import com.campus.auction.common.exception.BusinessException;
import com.campus.auction.dto.ReportDTO;
import com.campus.auction.dto.ReportHandleDTO;
import com.campus.auction.entity.CreditLog;
import com.campus.auction.entity.Item;
import com.campus.auction.entity.Notification;
import com.campus.auction.entity.Report;
import com.campus.auction.entity.User;
import com.campus.auction.mapper.CreditLogMapper;
import com.campus.auction.mapper.ItemMapper;
import com.campus.auction.mapper.NotificationMapper;
import com.campus.auction.mapper.ReportMapper;
import com.campus.auction.mapper.UserMapper;
import com.campus.auction.service.ReportService;
import com.campus.auction.vo.ReportVO;import com.campus.auction.util.TimeUtil;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    /** 举报核实后的信用扣分 */
    private static final int REPORT_PENALTY = 20;

    private final ReportMapper reportMapper;
    private final ItemMapper itemMapper;
    private final UserMapper userMapper;
    private final CreditLogMapper creditLogMapper;
    private final NotificationMapper notificationMapper;
    private final TransactionTemplate transactionTemplate;

    @Override
    public void submit(Long reporterId, ReportDTO dto) {
        if (!Report.TARGET_ITEM.equals(dto.getTargetType())
                && !Report.TARGET_USER.equals(dto.getTargetType())) {
            throw new BusinessException(ResultCode.BIZ_ERROR, "举报对象类型不合法");
        }
        if (dto.getTargetId() == null || dto.getTargetId() <= 0) {
            throw new BusinessException(ResultCode.BIZ_ERROR, "举报对象 ID 不合法");
        }
        // 对象必须真实存在
        if (Report.TARGET_ITEM.equals(dto.getTargetType())) {
            if (itemMapper.selectById(dto.getTargetId()) == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "举报的拍品不存在");
            }
        } else {
            if (userMapper.selectById(dto.getTargetId()) == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "举报的用户不存在");
            }
        }
        // 同一人对同一对象重复举报（未处理完的）直接幂等返回
        Long duplicated = reportMapper.selectCount(
                new LambdaQueryWrapper<Report>()
                        .eq(Report::getReporterId, reporterId)
                        .eq(Report::getTargetType, dto.getTargetType())
                        .eq(Report::getTargetId, dto.getTargetId())
                        .eq(Report::getStatus, Report.STATUS_PENDING));
        if (duplicated > 0) {
            throw new BusinessException(ResultCode.BIZ_ERROR, "你已举报过该对象，请等待处理");
        }

        Report report = new Report();
        report.setReporterId(reporterId);
        report.setTargetType(dto.getTargetType());
        report.setTargetId(dto.getTargetId());
        report.setReason(dto.getReason());
        report.setStatus(Report.STATUS_PENDING);
        report.setCreateTime(LocalDateTime.now());
        report.setUpdateTime(LocalDateTime.now());
        reportMapper.insert(report);
    }

    @Override
    public Map<String, Object> adminList(int page, int size, String status) {
        Page<Report> result = reportMapper.selectPage(
                new Page<>(page, Math.min(size, 50)),
                new LambdaQueryWrapper<Report>()
                        .eq(status != null && !status.isBlank(), Report::getStatus, status)
                        .orderByAsc(Report::getCreateTime));
        List<ReportVO> list = result.getRecords().stream().map(this::toVO).toList();
        return Map.of("list", list, "total", result.getTotal());
    }

    @Override
    public void handle(Long reportId, Long adminId, ReportHandleDTO dto) {
        boolean resolved = Report.STATUS_RESOLVED.equals(dto.getAction());
        if (!resolved && !Report.STATUS_DISMISSED.equals(dto.getAction())) {
            throw new BusinessException(ResultCode.BIZ_ERROR, "action 只能为 RESOLVED 或 DISMISSED");
        }

        transactionTemplate.executeWithoutResult(status -> {
            Report report = reportMapper.selectById(reportId);
            if (report == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "举报不存在");
            }
            if (!Report.STATUS_PENDING.equals(report.getStatus())) {
                throw new BusinessException(ResultCode.BIZ_ERROR, "该举报已处理");
            }
            // CAS：并发处理只生效一次
            int updated = reportMapper.update(null, new LambdaUpdateWrapper<Report>()
                    .eq(Report::getId, reportId)
                    .eq(Report::getStatus, Report.STATUS_PENDING)
                    .set(Report::getStatus, dto.getAction())
                    .set(Report::getHandlerId, adminId)
                    .set(Report::getHandleResult, dto.getHandleResult())
                    .set(Report::getUpdateTime, LocalDateTime.now()));
            if (updated == 0) {
                throw new BusinessException(ResultCode.BIZ_ERROR, "该举报已被处理");
            }

            if (resolved) {
                Long targetUserId = resolveTargetUserId(report);
                if (targetUserId != null) {
                    penalize(targetUserId, reportId);
                    notifyUser(targetUserId, "举报核实",
                            "你被举报且经管理员核实，信用分 -20（" + safeResult(dto) + "）。", reportId);
                }
            }
            // 驳回也通知举报人结果，形成闭环
            notifyUser(report.getReporterId(), "举报处理结果",
                    (resolved ? "你提交的举报已核实，被举报方已被扣除信用分。"
                              : "你提交的举报经核实不成立，已驳回。")
                            + safeResult(dto), reportId);
        });
    }

    /** ITEM 举报 → 拍品卖家；USER 举报 → 该用户 */
    private Long resolveTargetUserId(Report report) {
        if (Report.TARGET_USER.equals(report.getTargetType())) {
            return report.getTargetId();
        }
        Item item = itemMapper.selectById(report.getTargetId());
        return item == null ? null : item.getSellerId();
    }

    private void penalize(Long userId, Long reportId) {
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .setSql("credit_score = GREATEST(credit_score - " + REPORT_PENALTY + ", 0)"));
        CreditLog log = new CreditLog();
        log.setUserId(userId);
        log.setChangeValue(-REPORT_PENALTY);
        log.setReason("被举报且核实");
        log.setRefId(reportId);
        log.setCreateTime(LocalDateTime.now());
        creditLogMapper.insert(log);
    }

    private void notifyUser(Long userId, String title, String content, Long refId) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType("REPORT");
        n.setTitle(title);
        n.setContent(content);
        n.setRefId(refId);
        n.setIsRead(0);
        n.setCreateTime(LocalDateTime.now());
        notificationMapper.insert(n);
    }

    private String safeResult(ReportHandleDTO dto) {
        return dto.getHandleResult() == null || dto.getHandleResult().isBlank()
                ? "" : "处理说明：" + dto.getHandleResult();
    }

    private ReportVO toVO(Report r) {
        User reporter = userMapper.selectById(r.getReporterId());
        Long targetUserId = resolveTargetUserId(r);
        User targetUser = targetUserId == null ? null : userMapper.selectById(targetUserId);
        Item item = Report.TARGET_ITEM.equals(r.getTargetType())
                ? itemMapper.selectById(r.getTargetId()) : null;
        return ReportVO.builder()
                .id(r.getId())
                .targetType(r.getTargetType())
                .targetId(r.getTargetId())
                .reason(r.getReason())
                .status(r.getStatus())
                .reporter(reporter == null ? "已注销" : safeNickname(reporter))
                .targetUser(targetUser == null ? "已注销" : safeNickname(targetUser))
                .itemTitle(item == null ? null : item.getTitle())
                .handleResult(r.getHandleResult())
                .createTime(TimeUtil.str(r.getCreateTime()))
                .build();
    }

    private String safeNickname(User u) {
        return u.getNickname() == null ? "微信用户" : u.getNickname();
    }
}
