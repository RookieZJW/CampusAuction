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

/**
 * 认证服务：老用户静默登录 + 新用户一步登录。
 *
 * <p><b>账号锚点始终是 openid</b>（t_user.openid 非空唯一，全站 JWT subject 也是 openid）。
 * 手机号只是「登录凭证」：一步登录时先用手机号查账号，查到就进那个账号；
 * 查不到再用当前微信 openid 兜底注册。鉴权体系无需改动。
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
    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;

    /** 老用户登录：code 换 openid 后签发 token，不触碰资料（本地有登录态时前端直接用缓存） */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginVO login(String code) {
        String openid = wxApiService.code2Openid(code);

        User user = findByOpenid(openid);
        boolean isNew = false;
        if (user == null) {
            user = registerUser(openid, null, "微信用户");
            isNew = true;
            log.info("新用户注册（静默登录）：userId={}", user.getId());
        }

        checkNotBanned(user);
        return buildVO(user, isNew);
    }

    /**
     * 新用户一步登录：微信 code + 手机号 code + 昵称。
     * 手机号查已有账号（打通）→ 没有则用 openid 兜底 → 写入昵称手机号。
     * 头像由前端拿到 token 后上传并走 /users/profile 更新。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginVO register(String wxCode, String phoneCode, String nickname) {
        String openid = wxApiService.code2Openid(wxCode);
        String phone = wxApiService.getPhoneNumber(phoneCode);
        if (phone == null || phone.isBlank()) {
            throw new BusinessException(ResultCode.BIZ_ERROR, "未获取到手机号，请重新授权");
        }
        String cleanNickname = nickname == null ? "" : nickname.trim();
        if (cleanNickname.isEmpty()) {
            cleanNickname = "微信用户";
        }

        // ① 手机号已属于某账号 → 直接进入（账号打通）
        User user = findByPhone(phone);
        boolean isNew = false;
        if (user != null) {
            user.setNickname(cleanNickname);
            userMapper.updateById(user);
            log.info("一步登录：手机号命中已有账号 userId={}", user.getId());
        } else {
            // ② 手机号未绑定 → 用当前 openid 兜底：已有账号绑手机号，否则注册
            user = findByOpenid(openid);
            if (user != null) {
                user.setPhone(phone);
                user.setNickname(cleanNickname);
                userMapper.updateById(user);
                log.info("一步登录：手机号绑定到已有微信账号 userId={}", user.getId());
            } else {
                user = registerUser(openid, phone, cleanNickname);
                isNew = true;
                log.info("新用户一步注册：userId={}", user.getId());
            }
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

    /** 首次注册，手机号可为空 */
    private User registerUser(String openid, String phone, String nickname) {
        User user = new User();
        user.setOpenid(openid);
        user.setNickname(nickname);
        user.setPhone(phone);
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

    /** 用账号自身的 openid 签 token——账号打通的关键 */
    private LoginVO buildVO(User user, boolean isNew) {
        return LoginVO.builder()
                .token(jwtUtil.generateToken(user.getOpenid()))
                .userId(user.getId())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .phone(user.getPhone() == null ? "" : user.getPhone())
                .balance(user.getBalance())
                .creditScore(user.getCreditScore())
                .isNew(isNew)
                .build();
    }
}
