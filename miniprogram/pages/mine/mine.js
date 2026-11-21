// 我的：登录态展示 + 个人中心入口
const app = getApp()
const { request } = require('../../utils/request')
const avatarUtil = require('../../utils/avatar')

Page({
  data: {
    userInfo: null,
    balanceYuan: '0.00',
    unread: 0
  },

  onShow() {
    const userInfo = app.globalData.userInfo
    this.setData({
      userInfo,
      balanceYuan: userInfo ? (userInfo.balance / 100).toFixed(2) : '0.00',
      unread: 0
    })
    if (userInfo) {
      this.loadUnread()
      // 真机调试时服务端头像 URL 是 http+纯IP，image 组件会被微信拦截，转 base64 才能显示。
      // 不用一次性标志：从「完善资料」返回时 avatar 可能被写回 http URL，需要每次进来都校正。
      if (avatarUtil.isRemoteHttp(userInfo.avatar)) {
        this.loadRemoteAvatar(userInfo)
      }
    }
  },

  /** 把远程 http 头像转成本地 base64（仅真机调试演示需要，正式环境 https 域名无需转换） */
  loadRemoteAvatar(userInfo) {
    avatarUtil.remoteToBase64(userInfo.avatar).then((base64) => {
      if (!base64) {
        return
      }
      const updated = Object.assign({}, userInfo, { avatar: base64 })
      app.setLogin(updated)
      this.setData({ userInfo: updated })
    })
  },

  loadUnread() {
    request({
      url: '/api/v1/notifications',
      method: 'GET'
    }).then((data) => {
      this.setData({ unread: data.unread || 0 })
    })
  },

  onLoginTap() {
    wx.navigateTo({ url: '/pages/login/login' })
  },

  onMenuTap(e) {
    const url = e.currentTarget.dataset.url
    // 管理员入口无需用户登录（页内有独立的管理员登录）
    if (e.currentTarget.dataset.admin) {
      wx.navigateTo({ url })
      return
    }
    // 其余入口未登录统一先去登录页
    if (!app.globalData.userInfo) {
      wx.navigateTo({ url: '/pages/login/login' })
      return
    }
    wx.navigateTo({ url })
  },

  onLogout() {
    wx.showModal({
      title: '提示',
      content: '确定退出登录吗？',
      success: (res) => {
        if (res.confirm) {
          app.logout()
          this.setData({ userInfo: null, balanceYuan: '0.00' })
        }
      }
    })
  }
})
