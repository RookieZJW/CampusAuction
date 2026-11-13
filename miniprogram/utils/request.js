// 网络请求封装：Promise 化 + token 注入 + 统一错误处理
const { BASE_URL } = require('./config')

/**
 * 发起请求
 * @param {Object} options { url, method, data, needAuth }
 * @returns {Promise<any>} 成功 resolve 业务 data，失败 reject { code, msg }
 */
function request(options) {
  const { url, method = 'GET', data = {}, needAuth = true, token } = options

  return new Promise((resolve, reject) => {
    const header = { 'Content-Type': 'application/json' }
    if (token) {
      // 显式传入 token（管理员身份等场景）
      header['Authorization'] = 'Bearer ' + token
    } else if (needAuth) {
      const userInfo = wx.getStorageSync('userInfo')
      if (userInfo && userInfo.token) {
        header['Authorization'] = 'Bearer ' + userInfo.token
      }
    }

    wx.request({
      url: BASE_URL + url,
      method,
      data,
      header,
      success(res) {
        const body = res.data || {}
        if (body.code === 0) {
          resolve(body.data)
          return
        }
        // 登录失效：清空本地态并跳转登录页
        if (body.code === 2001 || body.code === 2002) {
          wx.removeStorageSync('userInfo')
          wx.navigateTo({ url: '/pages/login/login' })
        }
        reject(body)
      },
      fail(err) {
        wx.showToast({ title: '网络异常，请稍后重试', icon: 'none' })
        reject({ code: -1, msg: '网络异常' })
      }
    })
  })
}

module.exports = { request }
