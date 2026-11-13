// 拍卖详情：图集 + 价格 + 倒计时 + 出价面板 + 3 秒实时轮询
// 轮询用轻量接口 /auctions/{id}/live，只取 4 个字段做局部 setData（避免整对象刷新卡顿）
const { request } = require('../../utils/request')
const app = getApp()

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
    minNextYuan: '0.00',
    countdownText: '',
    urgent: false,
    statusText: '',
    // 出价面板
    showBidPanel: false,
    bidYuan: '',
    quickBids: [],
    bidding: false,
    extendedTip: false
  },

  onLoad(options) {
    this.setData({ id: options.id })
    this.loadDetail()
  },

  onHide() {
    // 页面隐藏暂停轮询，回到前台 onShow 恢复（省流量）
    this.stopLivePolling()
  },

  onShow() {
    if (this.data.detail) {
      this.startLivePolling()
    }
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
        minNextYuan: fen2yuan(detail.currentPrice + detail.increment),
        statusText: STATUS_TEXT[detail.status] || detail.status
      })
      this.startCountdown()
      this.startLivePolling()
    }).catch(() => {
      this.setData({ loading: false })
    })
  },

  /** 3 秒轮询轻量实时态：局部路径 setData 只刷变化字段 */
  startLivePolling() {
    this.stopLivePolling()
    this._liveTimer = setInterval(() => {
      request({
        url: `/api/v1/auctions/${this.data.id}/live`,
        method: 'GET',
        needAuth: false
      }).then((live) => {
        if (live.status !== 'ONGOING') {
          // 状态变化（成交/流拍）→ 整页重载拿最终数据与订单去向
          this.loadDetail()
          return
        }
        const fen2yuan = (fen) => ((fen || 0) / 100).toFixed(2)
        const priceChanged = live.currentPrice !== this.data.detail.currentPrice
        this.setData({
          'detail.currentPrice': live.currentPrice,
          'detail.bidCount': live.bidCount,
          'detail.endTime': live.endTime,
          currentPriceYuan: fen2yuan(live.currentPrice),
          minNextYuan: fen2yuan(live.currentPrice + this.data.detail.increment)
        })
        if (priceChanged) {
          wx.vibrateShort({ type: 'light' })
        }
      }).catch(() => { /* 轮询失败静默，下轮重试 */ })
    }, 3000)
  },

  stopLivePolling() {
    if (this._liveTimer) {
      clearInterval(this._liveTimer)
      this._liveTimer = null
    }
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

  // ---------- 出价面板 ----------

  onBidTap() {
    if (this.data.detail.status !== 'ONGOING') {
      return
    }
    if (!app.globalData.userInfo) {
      wx.navigateTo({ url: '/pages/login/login' })
      return
    }
    const min = parseFloat(this.data.minNextYuan)
    const inc = parseFloat(this.data.incrementYuan)
    // 快捷出价：最低价 / +1 幅度 / +2 幅度 / +5 幅度
    const quicks = [min, min + inc, min + inc * 2, min + inc * 5]
      .map(v => Number(v.toFixed(2)))
    this.setData({
      showBidPanel: true,
      bidYuan: String(min),
      quickBids: quicks
    })
  },

  onCloseBidPanel() {
    this.setData({ showBidPanel: false })
  },

  onBidInput(e) {
    this.setData({ bidYuan: e.detail.value })
  },

  onQuickBid(e) {
    this.setData({ bidYuan: String(e.currentTarget.dataset.price) })
  },

  onConfirmBid() {
    if (this.data.bidding) {
      return
    }
    const yuan = parseFloat(this.data.bidYuan)
    if (!yuan || yuan < 0.01) {
      wx.showToast({ title: '请输入有效金额', icon: 'none' })
      return
    }
    const price = Math.round(yuan * 100) // 元转分
    this.setData({ bidding: true })

    request({
      url: `/api/v1/auctions/${this.data.id}/bids`,
      method: 'POST',
      data: { price }
    }).then((data) => {
      const fen2yuan = (fen) => ((fen || 0) / 100).toFixed(2)
      this.setData({
        showBidPanel: false,
        'detail.currentPrice': data.currentPrice,
        'detail.bidCount': data.bidCount,
        'detail.endTime': data.endTime,
        currentPriceYuan: fen2yuan(data.currentPrice),
        minNextYuan: fen2yuan(data.minNext),
        extendedTip: !!data.extended
      })
      wx.vibrateShort({ type: 'medium' })
      wx.showToast({ title: data.extended ? '出价成功，拍卖已延时' : '出价成功，您已领先', icon: 'none' })
      this.loadDetail() // 重新拉取出价记录
    }).catch((e) => {
      wx.showToast({ title: e.msg || '出价失败', icon: 'none' })
    }).finally(() => {
      this.setData({ bidding: false })
    })
  },

  onUnload() {
    // 离开页面清理全部定时器，防内存泄漏
    if (this._timer) {
      clearInterval(this._timer)
    }
    this.stopLivePolling()
  }
})
