// 信用分中心：分数等级 + 变动明细
const { request } = require('../../../utils/request')

Page({
  data: {
    creditScore: 100,
    level: 'good',
    levelText: '信用良好',
    logs: []
  },

  onShow() {
    request({
      url: '/api/v1/users/credit',
      method: 'GET'
    }).then((data) => {
      const score = data.creditScore || 0
      let level = 'good'
      let levelText = '信用良好'
      if (score >= 110) {
        level = 'excellent'
        levelText = '信用优秀'
      } else if (score < 60) {
        level = 'low'
        levelText = '信用受限'
      }
      const logs = (data.logs || []).map(l => ({
        ...l,
        timeText: (l.createTime || '').replace('T', ' ').slice(0, 16)
      }))
      this.setData({ creditScore: score, level, levelText, logs })
    })
  }
})
