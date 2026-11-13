// 互评：星级选择 + 评语，提交 POST /reviews/orders/{id}
const { request } = require('../../../utils/request')

const RATE_TAGS = {
  5: '交易愉快',
  4: '不错',
  3: '一般',
  2: '较差',
  1: '很差'
}

Page({
  data: {
    orderId: null,
    title: '',
    rating: 5,
    ratingText: '交易愉快',
    stars: [1, 2, 3, 4, 5],
    content: '',
    submitting: false
  },

  onLoad(options) {
    this.setData({
      orderId: options.orderId,
      title: decodeURIComponent(options.title || '')
    })
  },

  onStarTap(e) {
    const rating = Number(e.currentTarget.dataset.star)
    this.setData({
      rating,
      ratingText: RATE_TAGS[rating] || ''
    })
  },

  onContentInput(e) {
    this.setData({ content: e.detail.value })
  },

  onSubmit() {
    if (this.data.submitting) {
      return
    }
    this.setData({ submitting: true })
    request({
      url: `/api/v1/reviews/orders/${this.data.orderId}`,
      method: 'POST',
      data: {
        rating: this.data.rating,
        content: this.data.content.trim() || null
      }
    }).then(() => {
      wx.showToast({ title: '评价成功', icon: 'success' })
      setTimeout(() => wx.navigateBack(), 800)
    }).catch((e) => {
      wx.showToast({ title: e.msg || '提交失败', icon: 'none' })
    }).finally(() => {
      this.setData({ submitting: false })
    })
  }
})
