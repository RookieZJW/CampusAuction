// 登录页：wx.login 拿 code → 服务端换 openid 并签发 JWT
const { request } = require('../../utils/request')
const app = getApp()

Page({
  data: {
    loading: false
  },

  onWxLogin() {
    if (this.data.loading) {
      return
    }
    this.setData({ loading: true })

    wx.login({
      success: (loginRes) => {
        if (!loginRes.code) {
          this.setData({ loading: false })
          wx.showToast({ title: '微信登录失败，请重试', icon: 'none' })
          return
        }

        // code 发到自己的服务端换取 openid + token
        // session_key 留在服务端，前端只保存自己的 token
        request({
          url: '/api/v1/auth/login',
          method: 'POST',
          data: { code: loginRes.code },
          needAuth: false
        }).then((data) => {
          app.setLogin(data)
          wx.showToast({ title: data.isNew ? '欢迎加入' : '欢迎回来', icon: 'none' })
          setTimeout(() => {
            wx.switchTab({ url: '/pages/index/index' })
          }, 600)
        }).catch((err) => {
          wx.showToast({ title: err.msg || '登录失败', icon: 'none' })
        }).finally(() => {
          this.setData({ loading: false })
        })
      },
      fail: () => {
        this.setData({ loading: false })
        wx.showToast({ title: '微信登录失败，请重试', icon: 'none' })
      }
    })
  }
})
