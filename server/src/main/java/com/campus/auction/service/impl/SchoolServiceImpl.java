package com.campus.auction.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.auction.common.ResultCode;
import com.campus.auction.common.exception.BusinessException;
import com.campus.auction.entity.Auction;
import com.campus.auction.entity.School;
import com.campus.auction.entity.User;
import com.campus.auction.mapper.AuctionMapper;
import com.campus.auction.mapper.SchoolMapper;
import com.campus.auction.mapper.UserMapper;
import com.campus.auction.service.SchoolService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SchoolServiceImpl implements SchoolService {

    private final SchoolMapper schoolMapper;
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
    public Map<String, Object> certify(String openid, Long schoolId) {
        User user = requireUserByOpenid(openid);
        School school = schoolId == null ? null : schoolMapper.selectById(schoolId);
        if (school == null || !Integer.valueOf(School.STATUS_ENABLED).equals(school.getStatus())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "学校不存在或已停用");
        }
        user.setSchoolId(school.getId());
        userMapper.updateById(user);
        log.info("用户 {} 完成学校认证：schoolId={} {}", user.getId(), school.getId(), school.getName());
        return schoolInfo(school, true);
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
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getOpenid, openid));
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
        // 游客或未认证用户：保持免登录浏览，不做拦截
        if (schoolId == null) {
            return;
        }
        Auction auction = auctionMapper.selectById(auctionId);
        if (auction == null) {
            return; // 不存在由上层 detail 返回 1004
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
        // 兼容历史数据：school_id 为 NULL 的拍品视为公共，任何学校可访问
        return auction.getSchoolId() == null || schoolId.equals(auction.getSchoolId());
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
}
