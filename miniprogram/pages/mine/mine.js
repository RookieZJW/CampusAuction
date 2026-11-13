// 我的：登录态展示 + 个人中心入口
const app = getApp()

Page({
  data: {
    userInfo: null,
    balanceYuan: '0.00'
  },

  onShow() {
    const userInfo = app.globalData.userInfo
    this.setData({
      userInfo,
      balanceYuan: userInfo ? (userInfo.balance / 100).toFixed(2) : '0.00'
    })
  },

  onLoginTap() {
    wx.navigateTo({ url: '/pages/login/login' })
  },

  onMenuTap(e) {
    const url = e.currentTarget.dataset.url
    // 未登录统一先去登录页
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
