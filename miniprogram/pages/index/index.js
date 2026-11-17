// 拍卖大厅：列表 + 分类筛选 + 倒计时
// W2 后端就绪后接口为 GET /api/v1/auctions?page=&size=&categoryId=
const { request } = require('../../utils/request')
const { parseServerTime } = require('../../utils/time')
const app = getApp()

Page({
  data: {
    categories: [],
    activeCategory: 0,
    auctionList: [],
    page: 1,
    size: 10,
    loading: false,
    noMore: false,
    today: ''
  },

  onLoad() {
    this.setData({ today: this.formatToday() })
    this.loadCategories()
    this.loadAuctions()
  },

  onShow() {
    // 进入小程序即弹登录框（可关闭，不强制；本次启动只弹一次）
    app.tryPromptLogin('pages/index/index')
  },

  /** 公告栏上的日期，像手写补上去的一样 */
  formatToday() {
    const d = new Date()
    const week = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
    return `${d.getMonth() + 1}月${d.getDate()}日 ${week[d.getDay()]}`
  },

  onPullDownRefresh() {
    this.setData({ page: 1, noMore: false, auctionList: [] })
    this.loadAuctions().finally(() => wx.stopPullDownRefresh())
  },

  onReachBottom() {
    if (this.data.noMore || this.data.loading) {
      return
    }
    this.loadAuctions()
  },

  loadCategories() {
    request({
      url: '/api/v1/categories',
      method: 'GET',
      needAuth: false
    }).then((list) => {
      this.setData({ categories: list || [] })
    }).catch(() => {
      // 分类加载失败不阻塞列表
    })
  },

  loadAuctions() {
    if (this.data.loading) {
      return Promise.resolve()
    }
    this.setData({ loading: true })

    const { page, size, activeCategory } = this.data
    return request({
      url: `/api/v1/auctions?page=${page}&size=${size}` +
           (activeCategory ? `&categoryId=${activeCategory}` : ''),
      method: 'GET',
      needAuth: false
    }).then((data) => {
      const list = (data && data.list) || []
      this.setData({
        auctionList: this.data.auctionList.concat(this.decorate(list)),
        page: page + 1,
        noMore: list.length < size
      })
      this.startCountdown()
    }).catch((err) => {
      // 后端未启动或接口未实现时给出可读提示
      wx.showToast({ title: err.msg || '列表加载失败', icon: 'none' })
    }).finally(() => {
      this.setData({ loading: false })
    })
  },

  /** 前端展示加工：分转元、倒计时文本 */
  decorate(list) {
    return list.map((item) => ({
      ...item,
      currentPriceYuan: (item.currentPrice / 100).toFixed(2),
      coverImage: (item.images && item.images[0]) || '/images/placeholder.png',
      countdownText: '计算中',
      urgent: false
    }))
  },

  /** 每 3 秒刷新倒计时文案（仅本地计算，不发请求） */
  startCountdown() {
    if (this._timer) {
      clearInterval(this._timer)
    }
    this._timer = setInterval(() => {
      const now = Date.now()
      const list = this.data.auctionList.map((item) => {
        const remain = parseServerTime(item.endTime) - now
        const valid = Number.isFinite(remain)
        return {
          ...item,
          urgent: valid && remain > 0 && remain <= 5 * 60 * 1000,
          // 解析失败时给占位符，不再误报「已结束」
          countdownText: valid ? (remain > 0 ? this.formatRemain(remain) : '已结束') : '--'
        }
      })
      this.setData({ auctionList: list })
    }, 3000)
  },

  formatRemain(ms) {
    const totalSec = Math.floor(ms / 1000)
    const h = Math.floor(totalSec / 3600)
    const m = Math.floor((totalSec % 3600) / 60)
    const s = totalSec % 60
    if (h > 0) {
      return `${h}时${m}分`
    }
    return `${m}分${s}秒`
  },

  onCategoryTap(e) {
    const id = Number(e.currentTarget.dataset.id)
    this.setData({ activeCategory: id, page: 1, noMore: false, auctionList: [] })
    this.loadAuctions()
  },

  onAuctionTap(e) {
    const id = e.currentTarget.dataset.id
    wx.navigateTo({ url: `/pages/detail/detail?id=${id}` })
  },

  onUnload() {
    // 离开页面必须清理定时器，防止内存泄漏
    if (this._timer) {
      clearInterval(this._timer)
    }
  }
})
