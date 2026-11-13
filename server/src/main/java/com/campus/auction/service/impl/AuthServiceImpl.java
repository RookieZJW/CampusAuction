package com.campus.auction.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.auction.common.ResultCode;
import com.campus.auction.common.exception.BusinessException;
import com.campus.auction.dto.LoginVO;
import com.campus.auction.entity.User;
import com.campus.auction.mapper.UserMapper;
import com.campus.auction.service.AuthService;
import com.campus.auction.service.WxApiService;
import com.campus.auction.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /** 注册赠送虚拟余额：100 元 = 10000 分 */
    private static final int INIT_BALANCE = 10000;
    /** 初始信用分 */
    private static final int INIT_CREDIT = 100;

    private final WxApiService wxApiService;
    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginVO login(String code) {
        String openid = wxApiService.code2Openid(code);

        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getOpenid, openid));

        boolean isNew = false;
        if (user == null) {
            user = register(openid);
            isNew = true;
            log.info("新用户注册：userId={}, openid={}", user.getId(), openid);
        }

        if (user.getStatus() != null && user.getStatus() == 1) {
            throw new BusinessException(ResultCode.FORBIDDEN, "账号已被封禁");
        }

        String token = jwtUtil.generateToken(openid);

        return LoginVO.builder()
                .token(token)
                .userId(user.getId())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .balance(user.getBalance())
                .creditScore(user.getCreditScore())
                .isNew(isNew)
                .build();
    }

    /** 首次登录自动注册 */
    private User register(String openid) {
        User user = new User();
        user.setOpenid(openid);
        user.setNickname("微信用户");
        user.setCreditScore(INIT_CREDIT);
        user.setBalance(INIT_BALANCE);
        user.setStatus(0);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        userMapper.insert(user);
        return user;
    }
}
