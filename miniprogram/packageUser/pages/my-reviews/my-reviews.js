// 我的评价：收到/发出双视角
const { request } = require('../../../utils/request')

Page({
  data: {
    tab: 'RECEIVED',
    list: []
  },

  onShow() {
    this.loadList()
  },

  loadList() {
    request({
      url: `/api/v1/reviews/${this.data.tab === 'RECEIVED' ? 'received' : 'sent'}`,
      method: 'GET'
    }).then((list) => {
      const decorated = (list || []).map(r => ({
        ...r,
        stars: [1, 2, 3, 4, 5],
        timeText: (r.createTime || '').replace('T', ' ').slice(0, 16)
      }))
      this.setData({ list: decorated })
    })
  },

  onTabTap(e) {
    this.setData({ tab: e.currentTarget.dataset.tab })
    this.loadList()
  }
})
