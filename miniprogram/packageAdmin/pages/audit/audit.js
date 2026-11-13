// 管理员审核列表：独立于用户身份的管理员登录（adminToken 单独存储）
const { request } = require('../../../utils/request')

Page({
  data: {
    adminToken: '',
    username: '',
    password: '',
    logging: false,
    loading: true,
    list: []
  },

  onShow() {
    const adminToken = wx.getStorageSync('adminToken')
    this.setData({ adminToken })
    if (adminToken) {
      this.loadList()
    } else {
      this.setData({ loading: false })
    }
  },

  onInput(e) {
    this.setData({ [e.currentTarget.dataset.field]: e.detail.value })
  },

  onAdminLogin() {
    if (this.data.logging) {
      return
    }
    if (!this.data.username || !this.data.password) {
      wx.showToast({ title: '请输入用户名和密码', icon: 'none' })
      return
    }
    this.setData({ logging: true })
    request({
      url: '/api/v1/admin/login',
      method: 'POST',
      data: { username: this.data.username, password: this.data.password },
      needAuth: false
    }).then((data) => {
      wx.setStorageSync('adminToken', data.token)
      this.setData({ adminToken: data.token, password: '' })
      this.loadList()
    }).catch((e) => {
      wx.showToast({ title: e.msg || '登录失败', icon: 'none' })
    }).finally(() => {
      this.setData({ logging: false })
    })
  },

  loadList() {
    this.setData({ loading: true })
    request({
      url: '/api/v1/admin/audit/items',
      method: 'GET',
      token: this.data.adminToken
    }).then((list) => {
      const decorated = (list || []).map(item => ({
        ...item,
        startPriceYuan: ((item.startPrice || 0) / 100).toFixed(2)
      }))
      this.setData({ list: decorated, loading: false })
    }).catch((e) => {
      this.setData({ loading: false })
      // token 失效则清空回到登录
      if (e.code === 2001 || e.code === 2002 || e.code === 2003) {
        wx.removeStorageSync('adminToken')
        this.setData({ adminToken: '' })
      }
    })
  },

  onAuditTap(e) {
    const item = this.data.list[e.currentTarget.dataset.index]
    wx.navigateTo({
      url: `/packageAdmin/pages/audit-detail/audit-detail?itemId=${item.itemId}`
    })
  },

  onAdminLogout() {
    wx.removeStorageSync('adminToken')
    this.setData({ adminToken: '', list: [] })
  }
})
