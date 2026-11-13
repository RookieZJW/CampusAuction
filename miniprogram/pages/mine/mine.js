// 我的：登录态展示 + 个人中心入口
const app = getApp()
const { request } = require('../../utils/request')

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
    }
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
