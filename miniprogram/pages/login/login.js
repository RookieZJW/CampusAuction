// 登录弹窗：微信 / 手机号 / 邮箱 三种方式，账号统一打通
// 协议勾选是登录前置条件（不默认勾选，不强制授权隐私信息）
const { request } = require('../../utils/request')
const app = getApp()

Page({
  data: {
    agreed: false,
    mode: 'main',            // main | email
    loading: false,          // 微信登录
    phoneLoading: false,     // 手机号登录
    emailLoading: false,     // 邮箱登录
    sending: false,          // 发送验证码中
    countdown: 0,            // 验证码重发倒计时（秒）
    email: '',
    emailCode: '',
    devCode: '',             // 开发模式固定码，演示提示用
    devMode: false
  },

  onUnload() {
    this.clearTimer()
  },

  clearTimer() {
    if (this._timer) {
      clearInterval(this._timer)
      this._timer = null
    }
  },

  startCountdown() {
    this.clearTimer()
    this.setData({ countdown: 60 })
    this._timer = setInterval(() => {
      const n = this.data.countdown - 1
      if (n <= 0) {
        this.clearTimer()
        this.setData({ countdown: 0 })
      } else {
        this.setData({ countdown: n })
      }
    }, 1000)
  },

  // ---------- 协议勾选 ----------
  onToggleAgree() {
    this.setData({ agreed: !this.data.agreed })
  },

  checkAgreed() {
    if (!this.data.agreed) {
      wx.showToast({ title: '请先阅读并同意用户协议和隐私政策', icon: 'none' })
      return false
    }
    return true
  },

  onOpenAgreement(e) {
    const type = e.currentTarget.dataset.type || 'user'
    wx.navigateTo({ url: '/pages/agreement/agreement?type=' + type })
  },

  // ---------- 弹窗关闭 ----------
  onMaskTap() {
    // 点遮罩关闭，与右上角 × 一致（可关闭，不强制登录）
    this.onClose()
  },

  onSheetTap() {
    // 卡片内点击不冒泡到遮罩，避免误关
  },

  onClose() {
    const pages = getCurrentPages()
    if (pages.length > 1) {
      wx.navigateBack()
    } else {
      wx.switchTab({ url: '/pages/index/index' })
    }
  },

  // ---------- 登录成功统一收尾 ----------
  finishLogin(data) {
    app.setLogin(data)
    const pages = getCurrentPages()
    wx.showToast({
      title: data.isNew ? '欢迎加入' : '欢迎回来',
      icon: 'none',
      duration: 500
    })
    setTimeout(() => {
      if (pages.length > 1) {
        wx.navigateBack()
      } else {
        wx.switchTab({ url: '/pages/index/index' })
      }
    }, 550)
  },

  // ---------- 微信登录 ----------
  onWxLogin() {
    if (!this.checkAgreed() || this.data.loading) {
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
        request({
          url: '/api/v1/auth/login',
          method: 'POST',
          data: { code: loginRes.code },
          needAuth: false
        }).then((data) => {
          this.finishLogin(data)
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
  },

  // ---------- 手机号登录（微信一键授权手机号）----------
  onGetPhoneNumber(e) {
    if (!this.checkAgreed()) {
      return
    }
    const d = e.detail || {}
    if (!d.code) {
      if (d.errno === 1400001) {
        wx.showToast({ title: '手机号体验额度已用完', icon: 'none' })
      } else {
        wx.showToast({ title: '已取消，可稍后再试', icon: 'none' })
      }
      return
    }
    if (this.data.phoneLoading) {
      return
    }
    this.setData({ phoneLoading: true })
    // phoneCode（换手机号）与 wxCode（账号打通兜底）都需要，并行获取
    wx.login({
      success: (loginRes) => {
        if (!loginRes.code) {
          this.setData({ phoneLoading: false })
          wx.showToast({ title: '微信登录失败，请重试', icon: 'none' })
          return
        }
        request({
          url: '/api/v1/auth/login/phone',
          method: 'POST',
          data: { phoneCode: d.code, wxCode: loginRes.code },
          needAuth: false
        }).then((data) => {
          this.finishLogin(data)
        }).catch((err) => {
          wx.showToast({ title: err.msg || '手机号登录失败', icon: 'none' })
        }).finally(() => {
          this.setData({ phoneLoading: false })
        })
      },
      fail: () => {
        this.setData({ phoneLoading: false })
        wx.showToast({ title: '微信登录失败，请重试', icon: 'none' })
      }
    })
  },

  // ---------- 邮箱登录 ----------
  onSwitchMode(e) {
    this.setData({ mode: e.currentTarget.dataset.mode || 'main' })
  },

  onEmailInput(e) {
    this.setData({ email: e.detail.value })
  },

  onCodeInput(e) {
    this.setData({ emailCode: e.detail.value })
  },

  onSendCode() {
    if (!this.checkAgreed()) {
      return
    }
    const email = (this.data.email || '').trim()
    if (!email || !email.includes('@')) {
      wx.showToast({ title: '请填写正确的邮箱', icon: 'none' })
      return
    }
    if (this.data.countdown > 0 || this.data.sending) {
      return
    }
    this.setData({ sending: true })
    request({
      url: '/api/v1/auth/email/code',
      method: 'POST',
      data: { email },
      needAuth: false
    }).then((data) => {
      const devCode = data.devCode || ''
      this.setData({
        devCode,
        devMode: !!data.devMode
      })
      this.startCountdown()
      wx.showToast({
        title: devCode ? '演示验证码：' + devCode : '验证码已发送',
        icon: 'none',
        duration: 3000
      })
    }).catch((err) => {
      wx.showToast({ title: err.msg || '发送失败', icon: 'none' })
    }).finally(() => {
      this.setData({ sending: false })
    })
  },

  onEmailLogin() {
    if (!this.checkAgreed() || this.data.emailLoading) {
      return
    }
    const email = (this.data.email || '').trim()
    const code = (this.data.emailCode || '').trim()
    if (!email || !email.includes('@')) {
      wx.showToast({ title: '请填写正确的邮箱', icon: 'none' })
      return
    }
    if (!code) {
      wx.showToast({ title: '请填写验证码', icon: 'none' })
      return
    }
    this.setData({ emailLoading: true })
    wx.login({
      success: (loginRes) => {
        if (!loginRes.code) {
          this.setData({ emailLoading: false })
          wx.showToast({ title: '微信登录失败，请重试', icon: 'none' })
          return
        }
        request({
          url: '/api/v1/auth/login/email',
          method: 'POST',
          data: { email, code, wxCode: loginRes.code },
          needAuth: false
        }).then((data) => {
          this.finishLogin(data)
        }).catch((err) => {
          wx.showToast({ title: err.msg || '邮箱登录失败', icon: 'none' })
        }).finally(() => {
          this.setData({ emailLoading: false })
        })
      },
      fail: () => {
        this.setData({ emailLoading: false })
        wx.showToast({ title: '微信登录失败，请重试', icon: 'none' })
      }
    })
  }
})
