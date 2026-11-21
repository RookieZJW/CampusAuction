// 我的订单：买入/卖出双视角 + 确认交割 + 约定地点
const { request } = require('../../../utils/request')
const imageUtil = require('../../../utils/image')

const STATUS_TEXT = {
  PENDING_TRADING: '待交割',
  COMPLETED: '已完成',
  CANCELLED: '已取消(违约)'
}

const PAY_STATUS_TEXT = {
  UNPAID: '未支付',
  PAYING: '支付中',
  PAID: '已支付',
  CLOSED: '已关闭支付单'
}

Page({
  data: {
    tab: 'BUYER',
    list: [],
    filteredList: [],
    loading: true
  },

  onShow() {
    this.loadList()
  },

  loadList() {
    this.setData({ loading: true })
    request({
      url: '/api/v1/orders/mine',
      method: 'GET'
    }).then((list) => {
      const decorated = (list || []).map(o => ({
        ...o,
        dealPriceYuan: ((o.dealPrice || 0) / 100).toFixed(2),
        statusText: STATUS_TEXT[o.status] || o.status,
        payStatus: o.payStatus || 'UNPAID',
        payStatusText: PAY_STATUS_TEXT[o.payStatus || 'UNPAID'] || '未支付'
      }))
      imageUtil.decorateList(decorated, ['coverImage']).then(() => {
        this.setData({ list: decorated, loading: false })
        this.applyFilter()
        this.markReviewed()
      })
    }).catch(() => {
      this.setData({ loading: false })
    })
  },

  /** W5：标记哪些已完成订单我已评价过（对照 /reviews/sent） */
  markReviewed() {
    request({
      url: '/api/v1/reviews/sent',
      method: 'GET'
    }).then((sent) => {
      const reviewedIds = new Set((sent || []).map(r => r.orderId))
      const list = this.data.list.map(o => ({
        ...o,
        reviewed: reviewedIds.has(o.orderId)
      }))
      this.setData({ list })
      this.applyFilter()
    }).catch(() => { /* 静默：评价状态拉取失败不阻塞列表 */ })
  },

  /** W5：跳转互评页 */
  onReview(e) {
    const { id, title } = e.currentTarget.dataset
    wx.navigateTo({
      url: `/packageUser/pages/review/review?orderId=${id}&title=${encodeURIComponent(title || '')}`
    })
  },

  onTabTap(e) {
    this.setData({ tab: e.currentTarget.dataset.tab })
    this.applyFilter()
  },

  applyFilter() {
    const { list, tab } = this.data
    this.setData({
      filteredList: list.filter(o => o.role === tab)
    })
  },

  /** 方案 B：跳转仿微信收银台（模拟支付） */
  onPay(e) {
    const orderId = e.currentTarget.dataset.id
    wx.navigateTo({
      url: `/packageUser/pages/cashier/cashier?orderId=${orderId}`
    })
  },

  /** 确认完成交割（仅买方） */
  onComplete(e) {
    const orderId = e.currentTarget.dataset.id
    wx.showModal({
      title: '确认完成交割',
      content: '确认已线下完成交易？确认后解冻保证金，双方信用分 +2',
      success: (res) => {
        if (!res.confirm) {
          return
        }
        request({
          url: `/api/v1/orders/${orderId}/complete`,
          method: 'POST'
        }).then(() => {
          wx.showToast({ title: '交易完成' })
          this.loadList()
        }).catch((err) => {
          wx.showToast({ title: err.msg || '操作失败', icon: 'none' })
        })
      }
    })
  },

  /** 约定交割地点（买卖双方均可） */
  onSetPlace(e) {
    const orderId = e.currentTarget.dataset.id
    wx.showModal({
      title: '约定交割地点',
      editable: true,
      placeholderText: '如：图书馆一楼大厅',
      success: (res) => {
        if (!res.confirm || !res.content || !res.content.trim()) {
          return
        }
        request({
          url: `/api/v1/orders/${orderId}/place`,
          method: 'POST',
          data: { place: res.content.trim() }
        }).then(() => {
          wx.showToast({ title: '已通知对方' })
          this.loadList()
        }).catch((err) => {
          wx.showToast({ title: err.msg || '操作失败', icon: 'none' })
        })
      }
    })
  }
})
