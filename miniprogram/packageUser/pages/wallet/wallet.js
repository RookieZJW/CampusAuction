// 钱包：虚拟余额 + 流水明细
const { request } = require('../../../utils/request')

const TYPE_TEXT = {
  GIFT: '注册赠送',
  FREEZE: '保证金冻结',
  UNFREEZE: '保证金解冻',
  DEDUCT: '违约扣除',
  REFUND: '退款'
}

Page({
  data: {
    balanceYuan: '0.00',
    logs: []
  },

  onShow() {
    request({
      url: '/api/v1/users/wallet',
      method: 'GET'
    }).then((data) => {
      const logs = (data.logs || []).map(l => ({
        ...l,
        typeText: TYPE_TEXT[l.type] || l.type,
        amountYuan: ((l.amount || 0) / 100).toFixed(2),
        timeText: (l.createTime || '').replace('T', ' ').slice(0, 16)
      }))
      this.setData({
        balanceYuan: ((data.balance || 0) / 100).toFixed(2),
        logs
      })
    })
  }
})
