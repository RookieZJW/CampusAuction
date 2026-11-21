// 审核详情：通过（立即开拍）/ 驳回（必填理由）
const { request } = require('../../../utils/request')
const imageUtil = require('../../../utils/image')

Page({
  data: {
    itemId: null,
    item: null,
    loading: true,
    approving: false,
    rejecting: false,
    startPriceYuan: '0.00',
    incrementYuan: '0.00',
    depositYuan: '0.00'
  },

  onLoad(options) {
    this.setData({ itemId: options.itemId })
    this.loadItem()
  },

  loadItem() {
    const adminToken = wx.getStorageSync('adminToken')
    if (!adminToken) {
      wx.showToast({ title: '请先登录管理员', icon: 'none' })
      setTimeout(() => wx.navigateBack(), 800)
      return
    }
    request({
      url: '/api/v1/admin/audit/items',
      method: 'GET',
      token: adminToken
    }).then((list) => {
      const item = (list || []).find(i => String(i.itemId) === String(this.data.itemId))
      if (!item) {
        this.setData({ item: null, loading: false })
        return
      }
      const fen2yuan = (fen) => ((fen || 0) / 100).toFixed(2)
      return imageUtil.decorateImages(item.images || []).then((images) => {
        this.setData({
          item: {
            ...item,
            images,
            submitTimeText: (item.submitTime || '').replace('T', ' ').slice(0, 16)
          },
          loading: false,
          startPriceYuan: fen2yuan(item.startPrice),
          incrementYuan: fen2yuan(item.increment),
          depositYuan: fen2yuan(item.deposit)
        })
      })
    }).catch(() => {
      this.setData({ loading: false })
    })
  },

  onApprove() {
    if (this.data.approving) {
      return
    }
    wx.showModal({
      title: '确认通过',
      content: `审核通过后拍卖立即开始，时长 ${this.data.item.durationHours} 小时`,
      success: (res) => {
        if (!res.confirm) {
          return
        }
        this.setData({ approving: true })
        request({
          url: `/api/v1/admin/audit/items/${this.data.itemId}/approve`,
          method: 'POST',
          token: wx.getStorageSync('adminToken')
        }).then(() => {
          wx.showToast({ title: '已通过，拍卖开始' })
          setTimeout(() => wx.navigateBack(), 800)
        }).catch((e) => {
          wx.showToast({ title: e.msg || '操作失败', icon: 'none' })
        }).finally(() => {
          this.setData({ approving: false })
        })
      }
    })
  },

  onReject() {
    if (this.data.rejecting) {
      return
    }
    // editable 弹窗输入驳回理由（基础库 ≥2.17.1，以官方文档核实）
    wx.showModal({
      title: '驳回理由',
      editable: true,
      placeholderText: '请填写驳回理由（必填）',
      success: (res) => {
        if (!res.confirm) {
          return
        }
        const reason = (res.content || '').trim()
        if (!reason) {
          wx.showToast({ title: '驳回必须填写理由', icon: 'none' })
          return
        }
        this.setData({ rejecting: true })
        request({
          url: `/api/v1/admin/audit/items/${this.data.itemId}/reject`,
          method: 'POST',
          data: { reason },
          token: wx.getStorageSync('adminToken')
        }).then(() => {
          wx.showToast({ title: '已驳回' })
          setTimeout(() => wx.navigateBack(), 800)
        }).catch((e) => {
          wx.showToast({ title: e.msg || '操作失败', icon: 'none' })
        }).finally(() => {
          this.setData({ rejecting: false })
        })
      }
    })
  }
})
