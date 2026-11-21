// 校园在线拍卖系统 小程序入口
const { request } = require('./utils/request')

App({
  globalData: {
    // 用户信息缓存：{ token, userId, nickname, avatar, phone, balance, creditScore }
    userInfo: null,
    // 本次启动是否已弹过登录框（避免反复弹）
    loginPrompted: false,
    // 本地登录态是否已完成恢复校验（老用户免登录判断用）
    loginReady: false
  },

  onLaunch() {
    // 启动时恢复本地登录态
    this.restoreLogin()
  },

  /** 首页进入时调用：未登录且本次启动未提示过，则弹一次登录框 */
  tryPromptLogin(fromPage) {
    if (this.globalData.userInfo || this.globalData.loginPrompted) {
      return false
    }
    // 登录页/协议页自身不触发，避免 navigateBack 后重复弹
    if (fromPage === 'pages/login/login' || fromPage === 'pages/agreement/agreement') {
      return false
    }
    this.globalData.loginPrompted = true
    wx.navigateTo({ url: '/pages/login/login' })
    return true
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
      // 兼容旧登录态：补齐学校字段（老用户迁移后库里已有 school_id，但本地缓存没有）
      if (userInfo.certified === undefined) {
        request({ url: '/api/v1/schools/mine' }).then((info) => {
          const cur = this.globalData.userInfo
          if (cur) {
            this.setLogin(Object.assign({}, cur, {
              schoolId: info.schoolId,
              schoolName: info.schoolName || '',
              certified: !!info.certified
            }))
          }
        }).catch(() => {})
      }
    }).catch(() => {
      wx.removeStorageSync('userInfo')
      this.globalData.userInfo = null
      this.globalData.loginReady = true
      // 老用户 token 过期：本次启动补弹一次登录框
      if (!this.globalData.loginPrompted) {
        this.globalData.loginPrompted = true
        wx.navigateTo({ url: '/pages/login/login' })
      }
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
