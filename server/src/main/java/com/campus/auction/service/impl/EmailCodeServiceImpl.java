package com.campus.auction.service.impl;

import com.campus.auction.common.ResultCode;
import com.campus.auction.common.exception.BusinessException;
import com.campus.auction.service.EmailCodeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 邮箱验证码实现。
 *
 * <p>开发模式（auth.email.dev-mode=true，默认）：验证码固定为 dev-code，
 * 不真实发信，把码写进日志并在接口返回里回显，方便答辩演示直接填写。
 * <p>生产模式：TODO 接入 SMTP（spring-boot-starter-mail + 本地注入密码），
 * 把 sendRealEmail() 实现即可，校验与限流逻辑无需改动。
 * <p>存储用 ConcurrentHashMap，单机毕设足够；若多实例部署需换 Redis。
 */
@Slf4j
@Service
public class EmailCodeServiceImpl implements EmailCodeService {

    private final SecureRandom random = new SecureRandom();

    /** email -> 验证码记录 */
    private final Map<String, CodeRecord> codeStore = new ConcurrentHashMap<>();

    /** email -> 上次发送时间戳（限流） */
    private final Map<String, Long> lastSendAt = new ConcurrentHashMap<>();

    @Value("${auth.email.dev-mode:true}")
    private boolean devMode;

    @Value("${auth.email.dev-code:123456}")
    private String devCode;

    @Value("${auth.email.code-ttl-minutes:5}")
    private int ttlMinutes;

    @Value("${auth.email.send-interval-seconds:60}")
    private int intervalSeconds;

    @Override
    public void sendCode(String email) {
        String key = normalize(email);
        long now = System.currentTimeMillis();

        // 60 秒限流，防止刷接口
        Long last = lastSendAt.get(key);
        if (last != null && now - last < intervalSeconds * 1000L) {
            long wait = (intervalSeconds * 1000L - (now - last)) / 1000 + 1;
            throw new BusinessException(ResultCode.BIZ_ERROR, "发送太频繁，" + wait + " 秒后再试");
        }

        String code = devMode ? devCode : String.format("%06d", random.nextInt(1_000_000));
        codeStore.put(key, new CodeRecord(code, now + ttlMinutes * 60_000L));
        lastSendAt.put(key, now);

        if (devMode) {
            // 开发演示：日志与接口回显，不真实发信
            log.warn("[邮箱验证码-开发模式] email={} code={}（未真实发信）", maskEmail(email), code);
        } else {
            sendRealEmail(email, code);
        }
    }

    @Override
    public void verifyCode(String email, String code) {
        String key = normalize(email);
        CodeRecord record = codeStore.get(key);
        if (record == null) {
            throw new BusinessException(ResultCode.BIZ_ERROR, "请先获取验证码");
        }
        if (System.currentTimeMillis() > record.expireAt) {
            codeStore.remove(key);
            throw new BusinessException(ResultCode.BIZ_ERROR, "验证码已过期，请重新获取");
        }
        if (!record.code.equals(code == null ? "" : code.trim())) {
            throw new BusinessException(ResultCode.BIZ_ERROR, "验证码不正确");
        }
        // 验证码一次性，用后即删
        codeStore.remove(key);
    }

    /** 开发模式返回固定码供前端演示提示；生产模式返回空串 */
    public String devCodeForDemo() {
        return devMode ? devCode : "";
    }

    /** 生产模式真实发信：后续拿到 SMTP 授权码后实现（spring-boot-starter-mail） */
    private void sendRealEmail(String email, String code) {
        // TODO: 接入 JavaMailSender，正文形如「您的验证码是 123456，5 分钟内有效」
        log.warn("[邮箱验证码-生产模式] 尚未接入 SMTP 发信，验证码={}（请补全 sendRealEmail）", code);
        throw new BusinessException(ResultCode.BIZ_ERROR, "邮件服务尚未配置，请联系管理员");
    }

    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private String maskEmail(String email) {
        int at = email == null ? -1 : email.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return email.substring(0, 1) + "***" + email.substring(at);
    }

    private static class CodeRecord {
        final String code;
        final long expireAt;

        CodeRecord(String code, long expireAt) {
            this.code = code;
            this.expireAt = expireAt;
        }
    }
}
