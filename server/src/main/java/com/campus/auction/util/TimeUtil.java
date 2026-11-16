package com.campus.auction.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 对外时间字符串统一格式。
 *
 * <p>不要直接用 {@code LocalDateTime.toString()}：它输出 ISO_LOCAL_DATE_TIME，
 * 形如 {@code 2026-11-17T04:02:34}（带 T，且秒为 0 时会省略秒）。
 * 小程序端若按 {@code yyyy-MM-dd HH:mm:ss} 假设做 {@code replace('-', '/')}，
 * 会得到非法日期导致倒计时算成 NaN。这里统一输出 {@code yyyy-MM-dd HH:mm:ss}。
 *
 * <p>本项目前后端同处 Asia/Shanghai，字符串不带时区，按本地时间解释。
 */
public final class TimeUtil {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private TimeUtil() {
    }

    /** 格式化为 yyyy-MM-dd HH:mm:ss；入参为 null 时返回 null。 */
    public static String str(LocalDateTime time) {
        return time == null ? null : time.format(FORMATTER);
    }
}
