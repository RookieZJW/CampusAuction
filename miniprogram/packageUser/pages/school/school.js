// 学校认证：选学校即认证，认证后只看本校拍品
const { request } = require('../../../utils/request')
const app = getApp()

Page({
  data: {
    schools: [],
    selectedId: null,
    schoolName: '',
    certified: false,
    loading: true,
    saving: false
  },

  onLoad() {
    this.loadSchools()
    this.loadMySchool()
  },

  loadSchools() {
    // 学校字典是公开接口，游客也能拉（认证页本身要求登录）
    request({ url: '/api/v1/schools', needAuth: false }).then((list) => {
      this.setData({ schools: list || [], loading: false })
    }).catch(() => {
      this.setData({ loading: false })
    })
  },

  loadMySchool() {
    request({ url: '/api/v1/schools/mine' }).then((info) => {
      this.setData({
        certified: !!info.certified,
        schoolName: info.schoolName || '',
        selectedId: info.schoolId || null
      })
    }).catch(() => {})
  },

  onSelect(e) {
    const id = Number(e.currentTarget.dataset.id)
    this.setData({ selectedId: id })
  },

  onSubmit() {
    if (this.data.saving) {
      return
    }
    if (!this.data.selectedId) {
      wx.showToast({ title: '请先选择学校', icon: 'none' })
      return
    }
    this.setData({ saving: true })
    request({
      url: '/api/v1/schools/auth',
      method: 'POST',
      data: { schoolId: this.data.selectedId }
    }).then((info) => {
      // 同步登录态：首页列表据此按本校过滤
      const u = app.globalData.userInfo
      if (u) {
        app.setLogin(Object.assign({}, u, {
          schoolId: info.schoolId,
          schoolName: info.schoolName,
          certified: true
        }))
      }
      wx.showToast({ title: '认证成功', icon: 'success' })
      setTimeout(() => {
        const pages = getCurrentPages()
        if (pages.length > 1) {
          wx.navigateBack()
        } else {
          wx.switchTab({ url: '/pages/index/index' })
        }
      }, 700)
    }).catch((err) => {
      wx.showToast({ title: err.msg || '认证失败，请重试', icon: 'none' })
    }).finally(() => {
      this.setData({ saving: false })
    })
  }
})
