package com.campus.auction.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.auction.common.ResultCode;
import com.campus.auction.common.exception.BusinessException;
import com.campus.auction.entity.Auction;
import com.campus.auction.entity.School;
import com.campus.auction.entity.SchoolAuth;
import com.campus.auction.entity.User;
import com.campus.auction.mapper.AuctionMapper;
import com.campus.auction.mapper.SchoolAuthMapper;
import com.campus.auction.mapper.SchoolMapper;
import com.campus.auction.mapper.UserMapper;
import com.campus.auction.service.SchoolService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SchoolServiceImpl implements SchoolService {

    private final SchoolMapper schoolMapper;
    private final SchoolAuthMapper schoolAuthMapper;
    private final UserMapper userMapper;
    private final AuctionMapper auctionMapper;

    @Override
    public List<Map<String, Object>> enabledSchools() {
        return schoolMapper.selectList(
                        new LambdaQueryWrapper<School>()
                                .eq(School::getStatus, School.STATUS_ENABLED)
                                .orderByAsc(School::getSort))
                .stream().map(s -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", s.getId());
                    m.put("name", s.getName());
                    m.put("shortName", s.getShortName());
                    return m;
                }).toList();
    }

    @Override
    public Map<String, Object> myAuth(String openid) {
        User user = requireUserByOpenid(openid);
        Map<String, Object> m = new LinkedHashMap<>();
        String status = user.getAuthStatus() == null ? "NONE" : user.getAuthStatus();
        m.put("status", status);
        m.put("certified", "APPROVED".equals(status) && user.getSchoolId() != null);
        m.put("schoolId", user.getSchoolId());

        School school = user.getSchoolId() == null ? null : schoolMapper.selectById(user.getSchoolId());
        m.put("schoolName", school == null ? "" : school.getName());
        m.put("shortName", school == null ? "" : school.getShortName());

        // 用户资料以 t_user 持久化值为准；存量数据兼容回退到申请表
        SchoolAuth latest = latestAuth(user.getId());
        m.put("realName", firstNonBlank(user.getRealName(), latest == null ? null : latest.getRealName()));
        m.put("studentNo", firstNonBlank(user.getStudentNo(), latest == null ? null : latest.getStudentNo()));
        m.put("studentCard", firstNonBlank(user.getStudentCard(), latest == null ? null : latest.getStudentCard()));
        m.put("authTime", user.getAuthTime() == null ? "" : user.getAuthTime().toString());
        m.put("applySchoolId", latest == null ? null : latest.getSchoolId());
        m.put("rejectReason", latest == null ? "" : nvl(latest.getRejectReason()));
        return m;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> submitAuth(String openid, Long schoolId, String realName,
                                          String studentNo, String studentCard) {
        User user = requireUserByOpenid(openid);
        School school = schoolId == null ? null : schoolMapper.selectById(schoolId);
        if (school == null || !Integer.valueOf(School.STATUS_ENABLED).equals(school.getStatus())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "学校不存在或已停用");
        }
        if (realName == null || realName.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请填写真实姓名");
        }
        if (studentNo == null || studentNo.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请填写学号");
        }
        if (studentCard == null || studentCard.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请上传学生证照片");
        }

        // 已认证用户重复提交同一学校：不重复走审核，直接返回当前状态
        if (SchoolAuth.STATUS_APPROVED.equals(user.getAuthStatus())
                && school.getId().equals(user.getSchoolId())) {
            return myAuth(openid);
        }

        // 已通过用户改学校：清掉旧 school_id，重新进入待审（失去旧学校身份）
        // 姓名/学号/学生证同步持久化到 t_user，不再只留在申请表
        user.setSchoolId(null);
        user.setAuthStatus("PENDING");
        user.setRealName(realName.trim());
        user.setStudentNo(studentNo.trim());
        user.setStudentCard(studentCard.trim());
        user.setAuthTime(null);
        userMapper.updateById(user);

        SchoolAuth auth = latestAuth(user.getId());
        if (auth == null) {
            auth = new SchoolAuth();
            auth.setUserId(user.getId());
            auth.setCreateTime(LocalDateTime.now());
        }
        auth.setSchoolId(school.getId());
        auth.setRealName(realName.trim());
        auth.setStudentNo(studentNo.trim());
        auth.setStudentCard(studentCard.trim());
        auth.setStatus(SchoolAuth.STATUS_PENDING);
        auth.setRejectReason(null);
        auth.setReviewerId(null);
        auth.setReviewTime(null);
        auth.setUpdateTime(LocalDateTime.now());
        if (auth.getId() == null) {
            schoolAuthMapper.insert(auth);
        } else {
            schoolAuthMapper.updateById(auth);
        }
        log.info("用户 {} 提交学校认证申请：schoolId={}", user.getId(), school.getId());

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", "PENDING");
        m.put("schoolId", null);
        m.put("schoolName", "");
        m.put("shortName", "");
        m.put("certified", false);
        m.put("rejectReason", "");
        return m;
    }

    @Override
    public List<Map<String, Object>> pendingAuths() {
        List<SchoolAuth> list = schoolAuthMapper.selectList(
                new LambdaQueryWrapper<SchoolAuth>()
                        .eq(SchoolAuth::getStatus, SchoolAuth.STATUS_PENDING)
                        .orderByAsc(SchoolAuth::getCreateTime));
        if (list.isEmpty()) {
            return List.of();
        }
        // 批量补用户与学校信息，避免 N+1
        Map<Long, User> userMap = userMapper.selectBatchIds(
                        list.stream().map(SchoolAuth::getUserId).distinct().toList())
                .stream().collect(java.util.stream.Collectors.toMap(User::getId, u -> u));
        Map<Long, School> schoolMap = schoolMapper.selectBatchIds(
                        list.stream().map(SchoolAuth::getSchoolId).distinct().toList())
                .stream().collect(java.util.stream.Collectors.toMap(School::getId, s -> s));

        return list.stream().map(a -> {
            User u = userMap.get(a.getUserId());
            School s = schoolMap.get(a.getSchoolId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("userId", a.getUserId());
            m.put("schoolId", a.getSchoolId());
            m.put("schoolName", s == null ? "" : s.getName());
            m.put("shortName", s == null ? "" : s.getShortName());
            m.put("nickname", u == null ? "" : nvl(u.getNickname()));
            m.put("avatar", u == null ? "" : nvl(u.getAvatar()));
            m.put("realName", nvl(a.getRealName()));
            m.put("studentNo", nvl(a.getStudentNo()));
            m.put("studentCard", nvl(a.getStudentCard()));
            m.put("status", a.getStatus());
            m.put("createTime", a.getCreateTime() == null ? "" : a.getCreateTime().toString());
            return m;
        }).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveAuth(Long authId, Long adminId) {
        SchoolAuth auth = requireAuth(authId);
        if (!SchoolAuth.STATUS_PENDING.equals(auth.getStatus())) {
            throw new BusinessException(ResultCode.BIZ_ERROR, "该申请已处理");
        }
        User user = userMapper.selectById(auth.getUserId());
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "申请人不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        auth.setStatus(SchoolAuth.STATUS_APPROVED);
        auth.setReviewerId(adminId);
        auth.setReviewTime(now);
        auth.setUpdateTime(now);
        schoolAuthMapper.updateById(auth);

        // 审核结果回写 t_user：以管理员审核时看到的那一份材料为准，完成持久化
        user.setSchoolId(auth.getSchoolId());
        user.setAuthStatus("APPROVED");
        user.setRealName(auth.getRealName());
        user.setStudentNo(auth.getStudentNo());
        user.setStudentCard(auth.getStudentCard());
        user.setAuthTime(now);
        userMapper.updateById(user);
        log.info("学校认证通过：authId={} userId={} adminId={}", authId, user.getId(), adminId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectAuth(Long authId, Long adminId, String reason) {
        SchoolAuth auth = requireAuth(authId);
        if (!SchoolAuth.STATUS_PENDING.equals(auth.getStatus())) {
            throw new BusinessException(ResultCode.BIZ_ERROR, "该申请已处理");
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请填写驳回理由");
        }
        auth.setStatus(SchoolAuth.STATUS_REJECTED);
        auth.setRejectReason(reason.trim());
        auth.setReviewerId(adminId);
        auth.setReviewTime(LocalDateTime.now());
        auth.setUpdateTime(LocalDateTime.now());
        schoolAuthMapper.updateById(auth);

        User user = userMapper.selectById(auth.getUserId());
        if (user != null) {
            user.setAuthStatus("REJECTED");
            user.setSchoolId(null);
            // 材料保留在 t_user，用户修改后重新提交会覆盖
            user.setRealName(firstNonBlank(user.getRealName(), auth.getRealName()));
            user.setStudentNo(firstNonBlank(user.getStudentNo(), auth.getStudentNo()));
            user.setStudentCard(firstNonBlank(user.getStudentCard(), auth.getStudentCard()));
            user.setAuthTime(null);
            userMapper.updateById(user);
        }
        log.info("学校认证驳回：authId={} adminId={} reason={}", authId, adminId, reason.trim());
    }

    @Override
    public Map<String, Object> mySchool(String openid) {
        User user = requireUserByOpenid(openid);
        if (user.getSchoolId() == null) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("schoolId", null);
            m.put("schoolName", "");
            m.put("shortName", "");
            m.put("certified", false);
            return m;
        }
        School school = schoolMapper.selectById(user.getSchoolId());
        return schoolInfo(school, true);
    }

    @Override
    public Long schoolIdOfOpenid(String openid) {
        if (openid == null || openid.isBlank()) {
            return null;
        }
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getOpenid, openid));
        return user == null ? null : user.getSchoolId();
    }

    @Override
    public Long schoolIdOfUser(Long userId) {
        if (userId == null) {
            return null;
        }
        User user = userMapper.selectById(userId);
        return user == null ? null : user.getSchoolId();
    }

    @Override
    public void assertReadable(String openid, Long auctionId) {
        Long schoolId = schoolIdOfOpenid(openid);
        if (schoolId == null) {
            return;
        }
        Auction auction = auctionMapper.selectById(auctionId);
        if (auction == null) {
            return;
        }
        if (!belongsToSchool(auction, schoolId)) {
            throw new BusinessException(ResultCode.CROSS_SCHOOL_DENIED);
        }
    }

    @Override
    public void assertTradable(Long userId, Long auctionId) {
        Long schoolId = schoolIdOfUser(userId);
        if (schoolId == null) {
            throw new BusinessException(ResultCode.SCHOOL_NOT_AUTHED);
        }
        Auction auction = auctionMapper.selectById(auctionId);
        if (auction == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "拍卖不存在");
        }
        if (!belongsToSchool(auction, schoolId)) {
            throw new BusinessException(ResultCode.CROSS_SCHOOL_DENIED);
        }
    }

    @Override
    public boolean belongsToSchool(Auction auction, Long schoolId) {
        if (auction == null || schoolId == null) {
            return false;
        }
        return auction.getSchoolId() == null || schoolId.equals(auction.getSchoolId());
    }

    private SchoolAuth latestAuth(Long userId) {
        return schoolAuthMapper.selectOne(
                new LambdaQueryWrapper<SchoolAuth>()
                        .eq(SchoolAuth::getUserId, userId)
                        .orderByDesc(SchoolAuth::getId)
                        .last("LIMIT 1"));
    }

    private SchoolAuth requireAuth(Long authId) {
        SchoolAuth auth = schoolAuthMapper.selectById(authId);
        if (auth == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "认证申请不存在");
        }
        return auth;
    }

    private User requireUserByOpenid(String openid) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getOpenid, openid));
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return user;
    }

    private Map<String, Object> schoolInfo(School school, boolean certified) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("schoolId", school == null ? null : school.getId());
        m.put("schoolName", school == null ? "" : school.getName());
        m.put("shortName", school == null ? "" : school.getShortName());
        m.put("certified", certified && school != null);
        return m;
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }

    /** 优先返回非空字符串，用于存量数据从申请表回退 */
    private String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : nvl(b);
    }
}
