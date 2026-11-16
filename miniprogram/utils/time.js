// 后端时间字符串统一解析（本项目前后端同处 Asia/Shanghai，时间按本地时区解释）
//
// 背景：后端历史上用 LocalDateTime.toString() 输出 ISO 格式，例如
//   "2026-11-17T04:02:34"   （带 T；秒为 0 时会省略成 "2026-11-17T04:02"）
// 而部分老代码按 "yyyy-MM-dd HH:mm:ss" 假设，直接 .replace(/-/g, '/') 会拼出
//   "2026/11/17T04:02:34" → Invalid Date → getTime() = NaN → 倒计时误显示「已结束」。
// 这里统一收口，兼容带 T / 带空格 / 带毫秒 / 带时区 / 省略秒等所有形态。

/**
 * 把后端返回的时间转成毫秒时间戳；无法解析时返回 NaN。
 * @param {string|number|null} value
 * @returns {number} epoch milliseconds，或 NaN
 */
function parseServerTime(value) {
  if (value === null || value === undefined || value === '') {
    return NaN
  }
  if (typeof value === 'number') {
    return value
  }

  const raw = String(value).trim()

  // 1) ISO 8601（带 T）：现代 WebView 原生支持，不带时区的部分按本地时间解释
  const native = new Date(raw).getTime()
  if (!isNaN(native)) {
    return native
  }

  // 2) 兜底：老格式 "yyyy-MM-dd HH:mm:ss" 在 iOS 上是 Invalid Date，
  //    需归一成 "yyyy/MM/dd HH:mm:ss"；顺带处理 T、毫秒、省略秒
  let s = raw
    .replace('T', ' ')
    .replace(/\.\d+/, '')
    .replace(/(Z|[+-]\d{2}:?\d{2})$/, '')
    .replace(/-/g, '/')
  if (/^\d{4}\/\d{1,2}\/\d{1,2} \d{1,2}:\d{2}$/.test(s)) {
    s += ':00'
  }
  const fallback = new Date(s).getTime()
  return isNaN(fallback) ? NaN : fallback
}

/**
 * 展示用：统一把 ISO 的 T 换成空格，可按长度截断（如 slice(0,16)）。
 * 无时区信息，按后端本地时间原样展示。
 */
function formatServerTime(value, length) {
  const s = String(value == null ? '' : value).trim().replace('T', ' ')
  return length ? s.slice(0, length) : s
}

module.exports = { parseServerTime, formatServerTime }
