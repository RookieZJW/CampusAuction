// 头像显示工具
// 背景：真机调试时服务端头像 URL 是 http:// + 局域网 IP，微信 <image> 组件会拦截，
// 只能显示占位图。这里统一把这类头像转成 base64（自包含）用于页面显示。
// 正式环境是 https 域名，isRemoteHttp 不匹配，直接用原 URL，无需转换。

/** 是否为真机调试场景的 http 外链头像（需要转 base64 才能显示） */
function isRemoteHttp(src) {
  return typeof src === 'string' && /^http:\/\//.test(src)
}

/** 本地图片文件压缩后转 base64（压缩避免超出 storage 单 key 1MB 限制） */
function toBase64(filePath) {
  return new Promise((resolve) => {
    if (!filePath) {
      resolve('')
      return
    }
    const read = (path) => {
      wx.getFileSystemManager().readFile({
        filePath: path,
        encoding: 'base64',
        success: (r) => resolve('data:image/jpeg;base64,' + r.data),
        fail: () => resolve('')
      })
    }
    if (wx.compressImage) {
      wx.compressImage({
        src: filePath,
        quality: 40,
        success: (c) => read(c.tempFilePath || filePath),
        fail: () => read(filePath)
      })
    } else {
      read(filePath)
    }
  })
}

/** 下载远程 http 头像并转 base64；失败返回空串，调用方保持原样即可 */
function remoteToBase64(url) {
  return new Promise((resolve) => {
    if (!isRemoteHttp(url)) {
      resolve('')
      return
    }
    wx.request({
      url,
      responseType: 'arraybuffer',
      success: (res) => {
        if (res.statusCode !== 200 || !res.data) {
          resolve('')
          return
        }
        try {
          resolve('data:image/jpeg;base64,' + wx.arrayBufferToBase64(res.data))
        } catch (e) {
          resolve('')
        }
      },
      fail: () => resolve('')
    })
  })
}

module.exports = { isRemoteHttp, toBase64, remoteToBase64 }
