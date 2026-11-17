// 登录弹窗：头像 + 昵称 + 手机号，一次填齐后点「登录」建号
// - 新用户：三项资料在弹窗内收集 → 一步建号 → 进入
// - 老用户：app.js 本地有登录态则静默进入，不弹此页
// 微信规则：头像 chooseAvatar / 昵称 type=nickname / 手机号 getPhoneNumber 是三个独立授权动作，
// 但都在这一屏完成，用户感知就是「登录时把资料填齐」。
const { request } = require('../../utils/request')
const { BASE_URL } = require('../../utils/config')
const app = getApp()

Page({
  data: {
    agreed: false,
    loading: false,
    nickname: '',
    avatarTemp: '',      // chooseAvatar 返回的临时路径
    avatarDisplay: '',   // 头像预览
    phoneCode: '',       // getPhoneNumber 回调的 code
    phoneMasked: '',     // 展示用（后端只回完整号，前端脱敏显示）
    phoneReady: false
  },

  // ---------- 协议 ----------
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
  onMaskTap() { this.onClose() },
  onSheetTap() {},

  onClose() {
    const pages = getCurrentPages()
    if (pages.length > 1) {
      wx.navigateBack()
    } else {
      wx.switchTab({ url: '/pages/index/index' })
    }
  },

  // ---------- 头像 ----------
  onChooseAvatar(e) {
    const url = (e.detail || {}).avatarUrl
    if (!url) {
      wx.showToast({ title: '换一张试试', icon: 'none' })
      return
    }
    // 只存临时路径，等拿到 token 后再上传持久化
    this.setData({ avatarTemp: url, avatarDisplay: url })
  },

  // ---------- 昵称 ----------
  onNicknameInput(e) {
    this.setData({ nickname: e.detail.value })
  },

  // 微信在 blur 时做内容安全检测，未通过会清空，这里以最终值为准
  onNicknameBlur(e) {
    this.setData({ nickname: e.detail.value || '' })
  },

  // ---------- 手机号 ----------
  onGetPhoneNumber(e) {
    const d = e.detail || {}
    if (!d.code) {
      if (d.errno === 1400001) {
        wx.showToast({ title: '手机号体验额度已用完', icon: 'none' })
      } else {
        wx.showToast({ title: '已取消授权，可重新点', icon: 'none' })
      }
      return
    }
    this.setData({ phoneCode: d.code, phoneReady: true })
    wx.showToast({ title: '手机号已授权', icon: 'success' })
  },

  /** 脱敏显示：138****1234 */
  maskPhone(phone) {
    if (!phone || phone.length < 7) {
      return phone || ''
    }
    return phone.slice(0, 3) + '****' + phone.slice(-4)
  },

  // ---------- 登录（一步建号）----------
  onLogin() {
    if (!this.checkAgreed() || this.data.loading) {
      return
    }
    const nickname = (this.data.nickname || '').trim()
    if (!nickname) {
      wx.showToast({ title: '先填个昵称吧', icon: 'none' })
      return
    }
    if (!this.data.phoneCode) {
      wx.showToast({ title: '请先授权手机号', icon: 'none' })
      return
    }

    this.setData({ loading: true })
    wx.login({
      success: (loginRes) => {
        if (!loginRes.code) {
          this.failLogin('微信登录失败，请重试')
          return
        }
        request({
          url: '/api/v1/auth/register',
          method: 'POST',
          data: {
            wxCode: loginRes.code,
            phoneCode: this.data.phoneCode,
            nickname: nickname
          },
          needAuth: false
        }).then((data) => {
          // 建号成功先写登录态，再用 token 上传头像
          app.setLogin(data)
          this.uploadAvatarThenFinish(data)
        }).catch((err) => {
          this.failLogin(err.msg || '登录失败，请重试')
        })
      },
      fail: () => this.failLogin('微信登录失败，请重试')
    })
  },

  /** 有选头像才上传；上传成功更新 profile 与本地登录态，然后进入 */
  uploadAvatarThenFinish(loginData) {
    const avatarTemp = this.data.avatarTemp
    if (!avatarTemp) {
      this.finishLogin(loginData)
      return
    }
    const token = loginData.token
    wx.uploadFile({
      url: BASE_URL + '/api/v1/upload/image',
      filePath: avatarTemp,
      name: 'file',
      header: { Authorization: 'Bearer ' + token },
      success: (res) => {
        let url = ''
        try {
          const body = JSON.parse(res.data)
          if (body.code === 0 && body.data && body.data.url) {
            url = body.data.url
          }
        } catch (e) { /* 上传失败不阻断登录，用默认头像 */ }

        if (!url) {
          this.finishLogin(loginData)
          return
        }
        // 把头像 URL 写入服务端与本地登录态
        request({
          url: '/api/v1/users/profile',
          method: 'PUT',
          data: { avatar: url }
        }).then((profile) => {
          app.setLogin(Object.assign({}, loginData, {
            avatar: profile.avatar || url,
            nickname: profile.nickname || loginData.nickname,
            phone: profile.phone || loginData.phone
          }))
          this.finishLogin(app.globalData.userInfo)
        }).catch(() => {
          this.finishLogin(loginData)
        })
      },
      fail: () => this.finishLogin(loginData)
    })
  },

  failLogin(msg) {
    this.setData({ loading: false })
    wx.showToast({ title: msg, icon: 'none' })
  },

  /** 登录成功收尾：回到来路页面 */
  finishLogin(data) {
    this.setData({ loading: false })
    app.setLogin(data)
    wx.showToast({
      title: data.isNew ? '欢迎加入' : '欢迎回来',
      icon: 'none',
      duration: 500
    })
    setTimeout(() => {
      const pages = getCurrentPages()
      if (pages.length > 1) {
        wx.navigateBack()
      } else {
        wx.switchTab({ url: '/pages/index/index' })
      }
    }, 550)
  }
})
