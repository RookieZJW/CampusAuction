// 管理员审核列表：独立于用户身份的管理员登录（adminToken 单独存储）
const { request } = require('../../../utils/request')
const imageUtil = require('../../../utils/image')

Page({
  data: {
    adminToken: '',
    username: '',
    password: '',
    logging: false,
    loading: true,
    list: [],
    tab: 'items',
    authList: [],
    previewCard: ''
  },

  onShow() {
    const adminToken = wx.getStorageSync('adminToken')
    this.setData({ adminToken })
    if (adminToken) {
      this.loadCurrent()
    } else {
      this.setData({ loading: false })
    }
  },

  onTabTap(e) {
    const tab = e.currentTarget.dataset.tab
    if (tab === this.data.tab) {
      return
    }
    this.setData({ tab })
    if (this.data.adminToken) {
      this.loadCurrent()
    }
  },

  loadCurrent() {
    if (this.data.tab === 'auths') {
      this.loadAuths()
    } else {
      this.loadList()
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
      this.loadCurrent()
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
      imageUtil.decorateList(decorated, ['images[0]']).then(() => {
        this.setData({ list: decorated, loading: false })
      })
    }).catch((e) => {
      this.setData({ loading: false })
      // token 失效则清空回到登录
      if (e.code === 2001 || e.code === 2002 || e.code === 2003) {
        wx.removeStorageSync('adminToken')
        this.setData({ adminToken: '' })
      }
    })
  },

  /** 学生证认证待审列表 */
  loadAuths() {
    this.setData({ loading: true })
    request({
      url: '/api/v1/admin/school-auths',
      method: 'GET',
      token: this.data.adminToken
    }).then((list) => {
      const decorated = (list || []).map(item => ({ ...item }))
      imageUtil.decorateList(decorated, ['studentCard']).then(() => {
        this.setData({
          authList: decorated.map(item => ({ ...item, studentCardPreview: item.studentCard })),
          loading: false
        })
      })
    }).catch((e) => {
      this.setData({ loading: false })
      this.handleAdminError(e)
    })
  },

  handleAdminError(e) {
    if (e.code === 2001 || e.code === 2002 || e.code === 2003) {
      wx.removeStorageSync('adminToken')
      this.setData({ adminToken: '', list: [], authList: [] })
    }
  },

  onPreviewCard(e) {
    const item = this.data.authList[e.currentTarget.dataset.index]
    if (!item || !item.studentCardPreview) {
      return
    }
    // 真机调试下图片是 base64，系统预览接口不支持，使用页内浮层看大图
    this.setData({ previewCard: item.studentCardPreview })
  },

  onClosePreview() {
    this.setData({ previewCard: '' })
  },

  onApproveAuth(e) {
    const item = this.data.authList[e.currentTarget.dataset.index]
    if (!item) {
      return
    }
    wx.showModal({
      title: '通过认证',
      content: `确认通过 ${item.realName}（${item.shortName || item.schoolName}）的学生证认证？`,
      success: (res) => {
        if (!res.confirm) {
          return
        }
        wx.showLoading({ title: '处理中', mask: true })
        request({
          url: `/api/v1/admin/school-auths/${item.id}/approve`,
          method: 'POST',
          token: this.data.adminToken
        }).then(() => {
          wx.hideLoading()
          wx.showToast({ title: '已通过', icon: 'success' })
          this.loadAuths()
        }).catch((err) => {
          wx.hideLoading()
          this.handleAdminError(err)
          wx.showToast({ title: err.msg || '操作失败', icon: 'none' })
        })
      }
    })
  },

  onRejectAuth(e) {
    const item = this.data.authList[e.currentTarget.dataset.index]
    if (!item) {
      return
    }
    wx.showModal({
      title: '驳回认证',
      editable: true,
      placeholderText: '请填写驳回理由，例如：学生证信息模糊',
      success: (res) => {
        if (!res.confirm) {
          return
        }
        const reason = (res.content || '').trim()
        if (!reason) {
          wx.showToast({ title: '请填写驳回理由', icon: 'none' })
          return
        }
        wx.showLoading({ title: '处理中', mask: true })
        request({
          url: `/api/v1/admin/school-auths/${item.id}/reject`,
          method: 'POST',
          data: { reason },
          token: this.data.adminToken
        }).then(() => {
          wx.hideLoading()
          wx.showToast({ title: '已驳回', icon: 'success' })
          this.loadAuths()
        }).catch((err) => {
          wx.hideLoading()
          this.handleAdminError(err)
          wx.showToast({ title: err.msg || '操作失败', icon: 'none' })
        })
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
