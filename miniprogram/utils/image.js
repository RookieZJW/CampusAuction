// 拍品图片显示工具
// 背景：真机调试时服务端图片 URL 是 http:// + 局域网 IP，微信 <image> 组件会拦截，
// 只能显示占位图。这里在数据层把这类 URL 转成 base64（自包含），页面 wxml 无需改动。
// 正式环境是 https 域名，isRemoteHttp 不匹配，原样返回，无需转换。
const { isRemoteHttp, remoteToBase64 } = require('./avatar')

// url -> base64 内存缓存：同一张图在多个页面/多次翻页出现时只下载一次
const cache = Object.create(null)

/** 单个图片 URL → 可显示值：http 外链转 base64，其余原样。失败返回空串，调用方保留原值 */
function displayUrl(url) {
  if (!url || !isRemoteHttp(url)) {
    return Promise.resolve(url || '')
  }
  if (cache[url]) {
    return Promise.resolve(cache[url])
  }
  return remoteToBase64(url).then((b64) => {
    const val = b64 || ''
    if (val) {
      cache[url] = val
    }
    return val
  })
}

/**
 * 批量把列表里指定的图片字段转成可显示值（原地修改，item 本身已是展开后的新对象）。
 * @param list 数据数组
 * @param fieldPaths 字段路径数组，支持普通字段（'coverImage'）和数组下标（'images[0]'）
 * @returns Promise<list>
 */
function decorateList(list, fieldPaths) {
  if (!Array.isArray(list) || !list.length) {
    return Promise.resolve(list)
  }
  const jobs = []
  list.forEach((item) => {
    fieldPaths.forEach((fp) => {
      const m = /^(\w+)\[(\d+)\]$/.exec(fp)
      if (m) {
        const key = m[1]
        const idx = Number(m[2])
        const arr = item[key]
        if (Array.isArray(arr) && arr[idx] && isRemoteHttp(arr[idx])) {
          jobs.push(displayUrl(arr[idx]).then((v) => { if (v) arr[idx] = v }))
        }
      } else if (item[fp] && isRemoteHttp(item[fp])) {
        jobs.push(displayUrl(item[fp]).then((v) => { if (v) item[fp] = v }))
      }
    })
  })
  return Promise.all(jobs).then(() => list)
}

/** 把图片数组转成可显示数组（详情页 swiper 用）：返回新数组，不改动原数组 */
function decorateImages(images) {
  if (!Array.isArray(images) || !images.length) {
    return Promise.resolve(Array.isArray(images) ? images : [])
  }
  return Promise.all(images.map((u) => displayUrl(u)))
}

module.exports = { isRemoteHttp, displayUrl, decorateList, decorateImages }
