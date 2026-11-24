// 校园在线拍卖系统 小程序入口
const { request } = require('./utils/request')

App({
  globalData: {
    // 用户信息缓存：{ token, userId, nickname, avatar, phone, balance, creditScore }
    userInfo: null,
    // 本地登录态是否已完成恢复校验（老用户免登录判断用）
    loginReady: false
  },

  onLaunch() {
    // 启动时恢复本地登录态
    this.restoreLogin()
  },

  /** 从本地缓存恢复 token 与用户信息，并做一次服务端校验 */
  restoreLogin() {
    const userInfo = wx.getStorageSync('userInfo')
    if (!userInfo || !userInfo.token) {
      // 本地无登录态：校验已完成（无账号），标记就绪
      this.globalData.loginReady = true
      return
    }
    this.globalData.userInfo = userInfo

    // 异步自检：token 失效则清空本地态，并弹登录框引导重新登录
    request({
      url: '/api/v1/auth/check',
      method: 'GET'
    }).then(() => {
      this.globalData.loginReady = true
      // 兼容旧登录态：补齐学校、审核状态与已持久化的认证资料
      if (userInfo.certified === undefined || userInfo.authStatus === undefined
          || userInfo.realName === undefined) {
        request({ url: '/api/v1/schools/mine' }).then((info) => {
          const cur = this.globalData.userInfo
          if (cur) {
            this.setLogin(Object.assign({}, cur, {
              schoolId: info.schoolId,
              schoolName: info.shortName || info.schoolName || '',
              certified: !!info.certified,
              authStatus: info.status || 'NONE',
              realName: info.realName || '',
              studentNo: info.studentNo || '',
              studentCard: info.studentCard || '',
              authTime: info.authTime || ''
            }))
          }
        }).catch(() => {})
      }
    }).catch(() => {
      // token 过期只静默清空登录态，不主动弹登录框。
      // 微信审核要求：先进首页浏览体验，用户需要身份时再自行授权登录。
      wx.removeStorageSync('userInfo')
      this.globalData.userInfo = null
      this.globalData.loginReady = true
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
