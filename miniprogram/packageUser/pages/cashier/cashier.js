// 仿微信支付收银台（方案 B：可插拔支付网关 + 模拟支付）
// 说明：演示环境不调 wx.requestPayment，不产生任何真实资金变动，仅更新 t_order 支付维度。
const { request } = require('../../../utils/request')

Page({
  data: {
    orderId: null,
    loading: true,
    paid: false,
    submitting: false,
    prepare: null,
    result: null,
    resultYuan: '0.00',
    title: '校园拍场成交订单'
  },

  onLoad(options) {
    const orderId = options.orderId
    if (!orderId) {
      wx.showToast({ title: '缺少订单信息', icon: 'none' })
      return
    }
    this.setData({ orderId })
    this.init()
  },

  init() {
    this.setData({ loading: true })
    // 先查支付状态：已支付直接展示成功页（幂等，重复进入不会重复支付）
    request({
      url: `/api/v1/orders/${this.data.orderId}/pay`,
      method: 'GET'
    }).then((st) => {
      if (st.payStatus === 'PAID') {
        this.showSuccess(st)
        return null
      }
      // 未支付/支付中/已关闭：发起支付（PAYING 下后端幂等返回已有支付单）
      return request({
        url: `/api/v1/orders/${this.data.orderId}/pay`,
        method: 'POST'
      }).then((p) => {
        this.setData({ prepare: p, title: p.title || this.data.title, loading: false })
      })
    }).catch((err) => {
      this.setData({ loading: false })
      wx.showToast({ title: err.msg || '加载失败', icon: 'none' })
    })
  },

  onConfirmPay() {
    if (this.data.submitting) {
      return
    }
    this.setData({ submitting: true })
    wx.showLoading({ title: '支付处理中', mask: true })
    request({
      url: `/api/v1/orders/${this.data.orderId}/pay/confirm`,
      method: 'POST'
    }).then((res) => {
      wx.hideLoading()
      this.setData({ submitting: false })
      this.showSuccess(res)
    }).catch((err) => {
      wx.hideLoading()
      this.setData({ submitting: false })
      wx.showToast({ title: err.msg || '支付失败', icon: 'none' })
    })
  },

  showSuccess(res) {
    this.setData({
      paid: true,
      result: res,
      resultYuan: ((res.amount || 0) / 100).toFixed(2),
      loading: false
    })
  },

  onCancel() {
    wx.showModal({
      title: '取消支付',
      content: '关闭支付单后可在订单列表重新发起支付',
      confirmText: '关闭支付单',
      success: (res) => {
        if (!res.confirm) {
          return
        }
        request({
          url: `/api/v1/orders/${this.data.orderId}/pay/close`,
          method: 'POST'
        }).then(() => {
          wx.navigateBack()
        }).catch((err) => {
          wx.showToast({ title: err.msg || '操作失败', icon: 'none' })
        })
      }
    })
  },

  onDone() {
    wx.navigateBack()
  }
})
