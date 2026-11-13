// 我的出价：领先/被超越/中标状态跟踪
const { request } = require('../../../utils/request')

const MY_STATUS = {
  LEADING: { text: '领先中', cls: 'leading' },
  OUTBID: { text: '被超越', cls: 'outbid' },
  WINNING: { text: '已中标', cls: 'winning' }
}

const AUCTION_STATUS = {
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
      url: '/api/v1/bids/mine',
      method: 'GET'
    }).then((list) => {
      const fen2yuan = (fen) => ((fen || 0) / 100).toFixed(2)
      const decorated = (list || []).map(item => ({
        ...item,
        myPriceYuan: fen2yuan(item.myPrice),
        currentPriceYuan: fen2yuan(item.currentPrice),
        myStatusText: MY_STATUS[item.myStatus] ? MY_STATUS[item.myStatus].text : item.myStatus,
        myStatusClass: MY_STATUS[item.myStatus] ? MY_STATUS[item.myStatus].cls : '',
        auctionStatusText: AUCTION_STATUS[item.auctionStatus] || item.auctionStatus || ''
      }))
      this.setData({ list: decorated, loading: false })
    }).catch(() => {
      this.setData({ loading: false })
    })
  },

  onBidTap(e) {
    wx.navigateTo({ url: `/pages/detail/detail?id=${e.currentTarget.dataset.id}` })
  }
})
