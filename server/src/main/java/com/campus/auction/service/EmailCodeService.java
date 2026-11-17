package com.campus.auction.service;

/**
 * 邮箱验证码服务：开发期用固定码 + 日志，生产可切真实 SMTP 发信。
 * 验证码存内存（单机毕设场景足够），5 分钟有效；同邮箱 60 秒限流。
 */
public interface EmailCodeService {

    /**
     * 生成并发送验证码。
     * dev-mode 下固定为配置的 dev-code，并把验证码写进日志供演示；
     * 生产模式下需实现真实 SMTP 发信（见实现类中的 TODO）。
     */
    void sendCode(String email);

    /** 校验验证码，错误/过期抛 BusinessException */
    void verifyCode(String email, String code);
}
