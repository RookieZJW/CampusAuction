// 登录页：wx.login 拿 code → 服务端换 openid 并签发 JWT
const { request } = require('../../utils/request')
const app = getApp()

Page({
  data: {
    loading: false
  },

  /** 打开小程序管理后台配置的《用户隐私保护指引》（微信官方页面） */
  onOpenPrivacyContract() {
    if (!wx.openPrivacyContract) {
      wx.showToast({ title: '当前微信版本不支持，请升级微信', icon: 'none' })
      return
    }
    wx.openPrivacyContract({
      fail: () => wx.showToast({ title: '请先在小程序后台配置隐私指引', icon: 'none' })
    })
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
