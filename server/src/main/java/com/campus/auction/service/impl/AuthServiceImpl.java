package com.campus.auction.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.auction.common.ResultCode;
import com.campus.auction.common.exception.BusinessException;
import com.campus.auction.dto.LoginVO;
import com.campus.auction.entity.User;
import com.campus.auction.mapper.UserMapper;
import com.campus.auction.service.AuthService;
import com.campus.auction.service.EmailCodeService;
import com.campus.auction.service.WxApiService;
import com.campus.auction.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 认证服务：三种登录方式统一到同一账号体系。
 *
 * <p><b>账号锚点始终是 openid</b>（t_user.openid 非空唯一，全站 JWT subject 也是 openid）。
 * 手机号 / 邮箱只是「登录凭证」：
 * <ol>
 *   <li>先用凭证查账号——查到就用<b>那个账号的 openid</b>签 token，因此同一手机号/邮箱
 *       在任何微信上登录都进同一个账号，订单/钱包/信用分互通；</li>
 *   <li>查不到再用当前微信 wx.login 的 openid 兜底：已有账号则把凭证绑上去，
 *       否则注册新账号并写入凭证。</li>
 * </ol>
 * 这样鉴权拦截器、AdminManageController 等一行都不用改。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /** 注册赠送虚拟余额：100 元 = 10000 分 */
    private static final int INIT_BALANCE = 10000;
    /** 初始信用分 */
    private static final int INIT_CREDIT = 100;

    private final WxApiService wxApiService;
    private final EmailCodeService emailCodeService;
    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginVO login(String code) {
        String openid = wxApiService.code2Openid(code);

        User user = findByOpenid(openid);
        boolean isNew = false;
        if (user == null) {
            user = register(openid, null, null);
            isNew = true;
            log.info("新用户注册（微信登录）：userId={}", user.getId());
        }

        checkNotBanned(user);
        return buildVO(user, isNew);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginVO loginByPhone(String phoneCode, String wxCode) {
        // 微信侧换手机号（code 5 分钟一次性，服务端 token 不下发前端）
        String phone = wxApiService.getPhoneNumber(phoneCode);
        if (phone == null || phone.isBlank()) {
            throw new BusinessException(ResultCode.BIZ_ERROR, "未获取到手机号，请重新授权");
        }

        // ① 凭证已存在 → 直接进入该账号（用该账号 openid 签 token）
        User user = findByPhone(phone);
        if (user != null) {
            checkNotBanned(user);
            log.info("手机号登录：userId={}（命中已有账号）", user.getId());
            return buildVO(user, false);
        }

        // ② 凭证不存在 → 用当前微信 openid 兜底：绑到已有账号或注册新账号
        String openid = wxApiService.code2Openid(wxCode);
        user = findByOpenid(openid);
        boolean isNew = false;
        if (user != null) {
            user.setPhone(phone);
            userMapper.updateById(user);
            log.info("手机号绑定到已有微信账号：userId={}", user.getId());
        } else {
            user = register(openid, phone, null);
            isNew = true;
            log.info("新用户注册（手机号登录）：userId={}", user.getId());
        }

        checkNotBanned(user);
        return buildVO(user, isNew);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginVO loginByEmail(String email, String code, String wxCode) {
        // 先校验验证码（一次性，用后即删）
        emailCodeService.verifyCode(email, code);

        String normalizedEmail = email.trim().toLowerCase();

        // ① 凭证已存在 → 直接进入该账号
        User user = findByEmail(normalizedEmail);
        if (user != null) {
            checkNotBanned(user);
            log.info("邮箱登录：userId={}（命中已有账号）", user.getId());
            return buildVO(user, false);
        }

        // ② 凭证不存在 → 用当前微信 openid 兜底：绑到已有账号或注册新账号
        String openid = wxApiService.code2Openid(wxCode);
        user = findByOpenid(openid);
        boolean isNew = false;
        if (user != null) {
            user.setEmail(normalizedEmail);
            userMapper.updateById(user);
            log.info("邮箱绑定到已有微信账号：userId={}", user.getId());
        } else {
            user = register(openid, null, normalizedEmail);
            isNew = true;
            log.info("新用户注册（邮箱登录）：userId={}", user.getId());
        }

        checkNotBanned(user);
        return buildVO(user, isNew);
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    private User findByOpenid(String openid) {
        return userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getOpenid, openid));
    }

    private User findByPhone(String phone) {
        return userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getPhone, phone));
    }

    private User findByEmail(String email) {
        return userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getEmail, email));
    }

    /** 首次登录自动注册，凭证可为空（微信登录两者都空） */
    private User register(String openid, String phone, String email) {
        User user = new User();
        user.setOpenid(openid);
        user.setNickname("微信用户");
        user.setPhone(phone);
        user.setEmail(email);
        user.setCreditScore(INIT_CREDIT);
        user.setBalance(INIT_BALANCE);
        user.setStatus(0);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        userMapper.insert(user);
        return user;
    }

    private void checkNotBanned(User user) {
        if (user.getStatus() != null && user.getStatus() == 1) {
            throw new BusinessException(ResultCode.FORBIDDEN, "账号已被封禁");
        }
    }

    /** 用账号自身的 openid 签 token——这是账号打通的关键 */
    private LoginVO buildVO(User user, boolean isNew) {
        return LoginVO.builder()
                .token(jwtUtil.generateToken(user.getOpenid()))
                .userId(user.getId())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .balance(user.getBalance())
                .creditScore(user.getCreditScore())
                .isNew(isNew)
                .build();
    }
}
