// 完善资料：头像昵称填写能力 + 手机号快速验证 + 隐私授权弹窗
// 头像/昵称/手机号全部由用户主动选择与授权，不做静默获取，也不强制授权。
const { request } = require('../../../utils/request')
const { BASE_URL } = require('../../../utils/config')
const app = getApp()

Page({
  data: {
    nickname: '',
    avatarDisplay: '',   // 本地预览用（选图后是临时路径）
    avatarUrl: '',       // 已上传到服务器的完整 URL，保存时提交这个
    uploading: false,
    phone: '',
    phoneBound: false,
    binding: false,
    saving: false,
    showPrivacy: false,
    privacyDeclined: false,
    privacyContractName: '《用户隐私保护指引》'
  },

  onLoad() {
    if (!app.globalData.userInfo) {
      wx.showToast({ title: '请先登录', icon: 'none' })
      setTimeout(() => wx.navigateTo({ url: '/pages/login/login' }), 800)
      return
    }
    this.loadProfile()
    this.initPrivacy()
  },

  onUnload() {
    if (wx.offNeedPrivacyAuthorization && this._privacyHandler) {
      wx.offNeedPrivacyAuthorization(this._privacyHandler)
    }
  },

  loadProfile() {
    request({ url: '/api/v1/users/profile', method: 'GET' }).then((p) => {
      this.setData({
        // 默认值「微信用户」不当成已填昵称
        nickname: p.nickname === '微信用户' ? '' : (p.nickname || ''),
        avatarUrl: p.avatar || '',
        avatarDisplay: p.avatar || '',
        phone: p.phone || '',
        phoneBound: !!p.phone
      })
    }).catch(() => { /* 静默：拉取失败不阻塞填写 */ })
  },

  // ---------- 隐私授权 ----------
  // 微信要求：只有在《小程序用户隐私保护指引》中声明过的信息类型才能调用对应接口。
  // 用户未同意时 input[type=nickname] 会降级成普通输入框，所以进页面先检查。
  initPrivacy() {
    if (!wx.getPrivacySetting) {
      return
    }
    wx.getPrivacySetting({
      success: (res) => {
        this.setData({
          privacyContractName: res.privacyContractName || '《用户隐私保护指引》'
        })
        if (res.needAuthorization) {
          this.setData({ showPrivacy: true })
        }
      },
      fail: () => {}
    })
    if (wx.onNeedPrivacyAuthorization) {
      this._privacyHandler = (resolve) => {
        this.resolvePrivacyAuthorization = resolve
        this.setData({ showPrivacy: true })
      }
      wx.onNeedPrivacyAuthorization(this._privacyHandler)
    }
  },

  onAgreePrivacy() {
    this.setData({ showPrivacy: false, privacyDeclined: false })
    if (this.resolvePrivacyAuthorization) {
      this.resolvePrivacyAuthorization({ buttonId: 'agree-btn', event: 'agree' })
      this.resolvePrivacyAuthorization = null
    }
  },

  onDisagreePrivacy() {
    // 不同意也允许继续浏览拍场，只是隐私相关组件暂不可用（不能强制授权）
    this.setData({ showPrivacy: false, privacyDeclined: true })
    if (this.resolvePrivacyAuthorization) {
      this.resolvePrivacyAuthorization({ event: 'disagree' })
      this.resolvePrivacyAuthorization = null
    }
    wx.showToast({ title: '可以先逛拍场，之后再来填', icon: 'none' })
  },

  onOpenPrivacyContract() {
    if (!wx.openPrivacyContract) {
      return
    }
    wx.openPrivacyContract({
      fail: () => wx.showToast({ title: '请先在小程序后台配置隐私指引', icon: 'none' })
    })
  },

  // ---------- 头像 ----------
  onChooseAvatar(e) {
    const url = (e.detail || {}).avatarUrl
    if (!url) {
      wx.showToast({ title: '换一张图试试', icon: 'none' })
      return
    }
    // chooseAvatar 返回的是临时路径，必须上传到自己的服务器持久化
    this.setData({ avatarDisplay: url, uploading: true })
    this.uploadAvatar(url)
  },

  uploadAvatar(filePath) {
    const token = app.globalData.userInfo && app.globalData.userInfo.token
    const fallback = this.data.avatarUrl
    wx.uploadFile({
      url: BASE_URL + '/api/v1/upload/image',
      filePath,
      name: 'file',
      header: { Authorization: token ? 'Bearer ' + token : '' },
      success: (res) => {
        try {
          const body = JSON.parse(res.data)
          if (body.code === 0 && body.data && body.data.url) {
            this.setData({ avatarUrl: body.data.url, avatarDisplay: body.data.url })
          } else {
            this.setData({ avatarDisplay: fallback })
            wx.showToast({ title: body.msg || '头像上传失败', icon: 'none' })
          }
        } catch (err) {
          this.setData({ avatarDisplay: fallback })
          wx.showToast({ title: '头像上传失败', icon: 'none' })
        }
      },
      fail: () => {
        this.setData({ avatarDisplay: fallback })
        wx.showToast({ title: '头像上传失败，请重试', icon: 'none' })
      },
      complete: () => this.setData({ uploading: false })
    })
  },

  // ---------- 昵称 ----------
  onNicknameInput(e) {
    this.setData({ nickname: e.detail.value })
  },

  onNicknameBlur(e) {
    // 微信在 blur 时异步做内容安全检测，未通过会清空输入
    this.setData({ nickname: e.detail.value || '' })
  },

  // ---------- 手机号 ----------
  onGetPhoneNumber(e) {
    if (this.data.binding) {
      return
    }
    const d = e.detail || {}
    if (!d.code) {
      if (d.errno === 1400001) {
        wx.showToast({ title: '手机号体验额度已用完', icon: 'none' })
      } else {
        wx.showToast({ title: '已取消，之后也能再绑定', icon: 'none' })
      }
      return
    }
    this.setData({ binding: true })
    request({
      url: '/api/v1/users/phone',
      method: 'POST',
      data: { code: d.code }
    }).then((profile) => {
      this.setData({ phone: profile.phone || '', phoneBound: !!profile.phone })
      wx.showToast({ title: '已绑定', icon: 'success' })
    }).catch((err) => {
      wx.showToast({ title: err.msg || '绑定失败，请重试', icon: 'none' })
    }).finally(() => {
      this.setData({ binding: false })
    })
  },

  // ---------- 保存 ----------
  onSave() {
    if (this.data.uploading) {
      wx.showToast({ title: '头像还在上传，稍等一下', icon: 'none' })
      return
    }
    const nickname = (this.data.nickname || '').trim()
    const avatar = this.data.avatarUrl || ''
    if (!nickname && !avatar) {
      wx.showToast({ title: '先选个头像或填个昵称', icon: 'none' })
      return
    }
    this.setData({ saving: true })
    request({
      url: '/api/v1/users/profile',
      method: 'PUT',
      data: { nickname: nickname || null, avatar: avatar || null }
    }).then((profile) => {
      this.syncLogin(profile)
      wx.showToast({ title: '已保存', icon: 'success' })
      setTimeout(() => wx.navigateBack(), 700)
    }).catch((err) => {
      wx.showToast({ title: err.msg || '保存失败', icon: 'none' })
    }).finally(() => {
      this.setData({ saving: false })
    })
  },

  /** 资料变更后同步本地登录态，让「我的」页立刻显示新头像昵称 */
  syncLogin(profile) {
    const info = app.globalData.userInfo
    if (!info) {
      return
    }
    app.setLogin({
      ...info,
      nickname: profile.nickname || info.nickname,
      avatar: profile.avatar || info.avatar
    })
  }
})
