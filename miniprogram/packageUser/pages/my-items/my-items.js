// 我发布的拍品：状态跟踪（待审/拍卖中/已驳回/已结束）
const { request } = require('../../../utils/request')

const STATUS_TEXT = {
  PENDING: '待审核',
  ON_SALE: '拍卖中',
  REJECTED: '已驳回',
  DRAFT: '草稿'
}
const AUCTION_TEXT = {
  ONGOING: '拍卖中',
  FINISHED: '已成交',
  FAILED: '已流拍',
  CANCELLED: '已取消'
}

Page({
  data: {
    list: [],
    loading: true
  },

  onShow() {
    this.loadList()
  },

  loadList() {
    request({
      url: '/api/v1/items/mine',
      method: 'GET'
    }).then((list) => {
      const decorated = (list || []).map(item => ({
        ...item,
        priceYuan: ((item.currentPrice || 0) / 100).toFixed(2),
        statusText: item.rejectReason
          ? '已驳回'
          : (AUCTION_TEXT[item.auctionStatus] || STATUS_TEXT[item.itemStatus] || item.itemStatus)
      }))
      this.setData({ list: decorated, loading: false })
    }).catch(() => {
      this.setData({ loading: false })
    })
  },

  onItemTap(e) {
    const id = e.currentTarget.dataset.id
    if (id) {
      wx.navigateTo({ url: `/pages/detail/detail?id=${id}` })
    }
  }
})
