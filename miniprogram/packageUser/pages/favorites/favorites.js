// 我的收藏：卡片列表 + 剩余时间显示 + 跳转详情
const { request } = require('../../../utils/request')
const imageUtil = require('../../../utils/image')

const STATUS_TEXT = {
  PENDING: '待开始',
  ONGOING: '进行中',
  FINISHED: '已成交',
  FAILED: '已流拍',
  CANCELLED: '已取消'
}

Page({
  data: {
    list: []
  },

  onShow() {
    this.loadList()
  },

  loadList() {
    request({
      url: '/api/v1/favorites/mine',
      method: 'GET'
    }).then((list) => {
      const fen2yuan = (fen) => ((fen || 0) / 100).toFixed(2)
      const now = Date.now()
      const items = (list || []).map(f => {
        let remainText = ''
        if (f.remainMs != null) {
          remainText = f.remainMs > now ? this.formatRemain(f.remainMs) : '已结束'
        }
        return {
          ...f,
          priceYuan: fen2yuan(f.currentPrice),
          statusText: STATUS_TEXT[f.status] || f.status,
          remainText
        }
      })
      imageUtil.decorateList(items, ['coverImage']).then(() => {
        this.setData({ list: items })
      })
    })
  },

  formatRemain(ms) {
    const totalSec = Math.floor(ms / 1000)
    const h = Math.floor(totalSec / 3600)
    const m = Math.floor((totalSec % 3600) / 60)
    const s = totalSec % 60
    if (h > 0) {
      return `${h} 时 ${m} 分`
    }
    return `${m} 分 ${s} 秒`
  },

  onItemTap(e) {
    wx.navigateTo({ url: `/pages/detail/detail?id=${e.currentTarget.dataset.id}` })
  },

  onCancelFavorite(e) {
    const id = e.currentTarget.dataset.id
    request({
      url: `/api/v1/favorites/auctions/${id}`,
      method: 'POST'
    }).then(() => {
      wx.showToast({ title: '已取消收藏', icon: 'none' })
      this.loadList()
    })
  }
})
