// 消息中心：竞价/结算/审核结果通知，支持单条与全部已读
const { request } = require('../../../utils/request')

const TYPE_TEXT = {
  AUCTION_WIN: '竞拍成交',
  AUCTION_END: '拍品结拍',
  OUTBID: '被超越',
  AUDIT_RESULT: '审核结果'
}

Page({
  data: {
    list: [],
    unread: 0,
    loading: false
  },

  onShow() {
    this.loadList()
  },

  loadList() {
    if (this.data.loading) return
    this.setData({ loading: true })
    request({
      url: '/api/v1/notifications',
      method: 'GET'
    }).then((data) => {
      const list = (data.list || []).map(n => ({
        ...n,
        typeText: TYPE_TEXT[n.type] || n.type,
        timeText: (n.createTime || '').replace('T', ' ').slice(0, 16)
      }))
      this.setData({ list, unread: data.unread || 0 })
    }).finally(() => {
      this.setData({ loading: false })
    })
  },

  onItemTap(e) {
    const id = e.currentTarget.dataset.id
    const item = this.data.list.find(n => n.id === id)
    if (!item) return
    // 点击未读消息 → 标记已读并刷新未读角标
    if (!item.isRead) {
      request({
        url: `/api/v1/notifications/${id}/read`,
        method: 'POST'
      }).then(() => {
        this.setData({
          [`list[${this.data.list.indexOf(item)}].isRead`]: 1,
          unread: Math.max(0, this.data.unread - 1)
        })
      })
    }
    // 有引用的拍品可跳转详情
    if (item.refId) {
      wx.navigateTo({ url: `/pages/detail/detail?id=${item.refId}` })
    }
  },

  onReadAllTap() {
    if (this.data.unread === 0) {
      wx.showToast({ title: '没有未读消息', icon: 'none' })
      return
    }
    request({
      url: '/api/v1/notifications/0/read',
      method: 'POST'
    }).then(() => {
      wx.showToast({ title: '已全部标记已读', icon: 'success' })
      this.loadList()
    })
  },

  onPullDownRefresh() {
    this.loadList()
  }
})
