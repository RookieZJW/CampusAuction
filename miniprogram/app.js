// 校园在线拍卖系统 小程序入口
const { request } = require('./utils/request')

App({
  globalData: {
    // 用户信息缓存：{ token, userId, nickname, avatar, balance, creditScore }
    userInfo: null
  },

  onLaunch() {
    // 启动时恢复本地登录态
    this.restoreLogin()
  },

  /** 从本地缓存恢复 token 与用户信息，并做一次服务端校验 */
  restoreLogin() {
    const userInfo = wx.getStorageSync('userInfo')
    if (!userInfo || !userInfo.token) {
      return
    }
    this.globalData.userInfo = userInfo

    // 异步自检：token 失效则清空本地态，交由页面引导重新登录
    request({
      url: '/api/v1/auth/check',
      method: 'GET'
    }).catch(() => {
      wx.removeStorageSync('userInfo')
      this.globalData.userInfo = null
    })
  },

  /** 登录成功后由 login 页调用，统一入口写入 */
  setLogin(data) {
    this.globalData.userInfo = data
    wx.setStorageSync('userInfo', data)
  },

  /** 退出登录 */
  logout() {
    wx.removeStorageSync('userInfo')
    this.globalData.userInfo = null
  }
})
