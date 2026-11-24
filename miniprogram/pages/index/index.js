// 拍卖大厅：列表 + 分类筛选 + 倒计时
// W2 后端就绪后接口为 GET /api/v1/auctions?page=&size=&categoryId=
const { request } = require('../../utils/request')
const { parseServerTime } = require('../../utils/time')
const imageUtil = require('../../utils/image')
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
    today: '',
    schoolLabel: '同校面交'
  },

  onLoad() {
    this.setData({ today: this.formatToday() })
    // 先用本地缓存秒填分类，避免真机首屏空白；随后以接口结果为准刷新
    // 缓存内容来自服务端 /api/v1/categories，不是前端造的假数据
    try {
      const cached = wx.getStorageSync('categories')
      if (Array.isArray(cached) && cached.length) {
        this.setData({ categories: cached })
      }
    } catch (e) { /* 缓存不可用时忽略，走网络请求 */ }
    this.loadCategories()
    this.loadAuctions()
  },

  onShow() {
    // 微信审核要求：先进首页浏览、体验功能后再由用户自行选择登录，
    // 禁止一进首页就弹授权。登录改由「我的 / 出价 / 收藏 / 发布」等
    // 需要身份的功能点按需引导。
    // 真机调试下首屏请求可能被登录弹窗/网络抖动打断，回到首页时若分类为空则补拉一次
    if (!this.data.categories || this.data.categories.length === 0) {
      this.loadCategories()
    }
    // 认证后返回首页：更新学校标签并按本校重新加载列表
    const u = app.globalData.userInfo
    const label = u && u.certified && u.schoolName ? (u.schoolName + ' · 本校') : '同校面交'
    if (label !== this.data.schoolLabel) {
      this.setData({ schoolLabel: label })
    }
    if (this._lastCertified !== undefined && this._lastCertified !== !!(u && u.certified)) {
      this._lastCertified = !!(u && u.certified)
      this.setData({ page: 1, noMore: false, auctionList: [] })
      this.loadAuctions()
    } else {
      this._lastCertified = !!(u && u.certified)
    }
  },

  /** 公告栏上的日期，像手写补上去的一样 */
  formatToday() {
    const d = new Date()
    const week = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
    return `${d.getMonth() + 1}月${d.getDate()}日 ${week[d.getDay()]}`
  },

  onPullDownRefresh() {
    this.setData({ page: 1, noMore: false, auctionList: [] })
    this.loadCategories()
    this.loadAuctions().finally(() => wx.stopPullDownRefresh())
  },

  onReachBottom() {
    if (this.data.noMore || this.data.loading) {
      return
    }
    this.loadAuctions()
  },

  /** 分类字典：公开接口。失败不再静默，带重试 + onShow/下拉补拉，避免真机上只剩「全部」 */
  loadCategories(retry = 0) {
    if (this._catLoading) {
      return
    }
    this._catLoading = true
    request({
      url: '/api/v1/categories',
      method: 'GET',
      needAuth: false
    }).then((list) => {
      this._catLoading = false
      const categories = Array.isArray(list) ? list : []
      if (categories.length === 0) {
        this.scheduleCategoryRetry(retry)
        return
      }
      this.setData({ categories })
      try { wx.setStorageSync('categories', categories) } catch (e) { /* 忽略 */ }
    }).catch((err) => {
      this._catLoading = false
      console.warn('[index] 分类加载失败', err)
      this.scheduleCategoryRetry(retry)
    })
  },

  /** 最多重试 2 次（间隔 600ms）；仍失败读本地缓存兜底，并给出可读提示 */
  scheduleCategoryRetry(retry) {
    if (retry >= 2) {
      try {
        const cached = wx.getStorageSync('categories')
        if (Array.isArray(cached) && cached.length) {
          this.setData({ categories: cached })
          wx.showToast({ title: '分类用本地缓存，下拉可更新', icon: 'none' })
          return
        }
      } catch (e) { /* 忽略，走提示 */ }
      wx.showToast({ title: '分类加载失败，下拉刷新重试', icon: 'none' })
      return
    }
    clearTimeout(this._catRetryTimer)
    this._catRetryTimer = setTimeout(() => this.loadCategories(retry + 1), 600)
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
      method: 'GET'
      // 不设 needAuth:false：已登录时带 token，后端按本校过滤；游客无 token 看全部
    }).then((data) => {
      const list = (data && data.list) || []
      const decorated = this.decorate(list)
      // 真机调试：封面 http+IP 转 base64 后再渲染
      return imageUtil.decorateList(decorated, ['coverImage']).then(() => decorated)
    }).then((decorated) => {
      this.setData({
        auctionList: this.data.auctionList.concat(decorated),
        page: page + 1,
        noMore: decorated.length < size
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
    if (this._catRetryTimer) {
      clearTimeout(this._catRetryTimer)
    }
  }
})
