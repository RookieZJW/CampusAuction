// 拍卖详情：图集 + 价格 + 倒计时 + 出价记录
// 出价功能 W3 上线，当前出价按钮引导登录/提示
const { request } = require('../../utils/request')

const STATUS_TEXT = {
  PENDING: '待审核',
  ONGOING: '进行中',
  FINISHED: '已成交',
  FAILED: '已流拍',
  CANCELLED: '已取消'
}

Page({
  data: {
    id: null,
    detail: null,
    loading: true,
    currentPriceYuan: '0.00',
    startPriceYuan: '0.00',
    incrementYuan: '0.00',
    countdownText: '',
    urgent: false,
    statusText: ''
  },

  onLoad(options) {
    this.setData({ id: options.id })
    this.loadDetail()
  },

  loadDetail() {
    request({
      url: `/api/v1/auctions/${this.data.id}`,
      method: 'GET',
      needAuth: false
    }).then((detail) => {
      const fen2yuan = (fen) => ((fen || 0) / 100).toFixed(2)
      const records = (detail.bidRecords || []).map(r => ({
        ...r,
        priceYuan: fen2yuan(r.price),
        bidTimeText: (r.bidTime || '').replace('T', ' ').slice(5, 16)
      }))
      this.setData({
        detail: { ...detail, bidRecords: records },
        loading: false,
        currentPriceYuan: fen2yuan(detail.currentPrice),
        startPriceYuan: fen2yuan(detail.startPrice),
        incrementYuan: fen2yuan(detail.increment),
        statusText: STATUS_TEXT[detail.status] || detail.status
      })
      this.startCountdown()
    }).catch(() => {
      this.setData({ loading: false })
    })
  },

  startCountdown() {
    if (this._timer) {
      clearInterval(this._timer)
    }
    this._timer = setInterval(() => {
      const detail = this.data.detail
      if (!detail || !detail.endTime || detail.status !== 'ONGOING') {
        return
      }
      const remain = new Date(detail.endTime.replace(/-/g, '/')).getTime() - Date.now()
      this.setData({
        urgent: remain <= 5 * 60 * 1000,
        countdownText: remain > 0 ? this.formatRemain(remain) : '已结束'
      })
    }, 1000)
  },

  formatRemain(ms) {
    const totalSec = Math.floor(ms / 1000)
    const h = Math.floor(totalSec / 3600)
    const m = Math.floor((totalSec % 3600) / 60)
    const s = totalSec % 60
    if (h > 0) {
      return `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
    }
    return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
  },

  onPreviewImage(e) {
    wx.previewImage({
      current: this.data.detail.images[e.currentTarget.dataset.index],
      urls: this.data.detail.images
    })
  },

  onBidTap() {
    // W3 竞价核心上线前的占位交互
    wx.showModal({
      title: '出价功能即将上线',
      content: '竞价模块正在开发中（W3），本周内开放出价',
      showCancel: false
    })
  },

  onUnload() {
    if (this._timer) {
      clearInterval(this._timer)
    }
  }
})
