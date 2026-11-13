// 我的订单：买入/卖出双视角 + 确认交割 + 约定地点
const { request } = require('../../../utils/request')

const STATUS_TEXT = {
  PENDING_TRADING: '待交割',
  COMPLETED: '已完成',
  CANCELLED: '已取消(违约)'
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
        statusText: STATUS_TEXT[o.status] || o.status
      }))
      this.setData({ list: decorated, loading: false })
      this.applyFilter()
    }).catch(() => {
      this.setData({ loading: false })
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
