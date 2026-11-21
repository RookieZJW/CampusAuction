// 发布拍品：表单 + 图片压缩上传 + 提交审核
const { request } = require('../../utils/request')
const { BASE_URL } = require('../../utils/config')
const app = getApp()

const CONDITIONS = ['全新', '九成新', '八成新', '七成新', '明显使用痕迹']
const DURATIONS = [
  { label: '2 小时', hours: 2 },
  { label: '6 小时', hours: 6 },
  { label: '1 天', hours: 24 },
  { label: '3 天', hours: 72 },
  { label: '7 天', hours: 168 }
]

Page({
  data: {
    title: '',
    description: '',
    categoryId: null,
    categoryIndex: 0,
    categoryNames: [],
    categories: [],
    conditions: CONDITIONS,
    condition: '',
    images: [],
    startPriceYuan: '',
    incrementYuan: '1',
    durations: DURATIONS,
    durationLabels: DURATIONS.map(d => d.label),
    durationIndex: 2,
    submitting: false
  },

  onLoad() {
    this.loadCategories()
  },

  loadCategories() {
    request({
      url: '/api/v1/categories',
      method: 'GET',
      needAuth: false
    }).then((list) => {
      this.setData({
        categories: list || [],
        categoryNames: (list || []).map(c => c.name)
      })
    })
  },

  onInput(e) {
    this.setData({ [e.currentTarget.dataset.field]: e.detail.value })
  },

  onCategoryChange(e) {
    const index = Number(e.detail.value)
    this.setData({
      categoryIndex: index,
      categoryId: this.data.categories[index] ? this.data.categories[index].id : null
    })
  },

  onConditionChange(e) {
    this.setData({ condition: CONDITIONS[Number(e.detail.value)] })
  },

  onDurationChange(e) {
    this.setData({ durationIndex: Number(e.detail.value) })
  },

  /** 选图 → 压缩 → 上传 → 收集 URL */
  onChooseImage() {
    const remain = 3 - this.data.images.length
    wx.chooseMedia({
      count: remain,
      mediaType: ['image'],
      sizeType: ['compressed'],
      success: (res) => {
        const tasks = res.tempFiles.map(file => this.compressAndUpload(file.tempFilePath))
        Promise.all(tasks).then((urls) => {
          this.setData({ images: this.data.images.concat(urls.filter(Boolean)) })
        }).catch(() => {
          wx.showToast({ title: '部分图片上传失败', icon: 'none' })
        })
      }
    })
  },

  compressAndUpload(filePath) {
    return new Promise((resolve, reject) => {
      // 压缩到 720px 宽以内（质量 80），控制流量（E-10 优化点）
      wx.compressImage({
        src: filePath,
        quality: 80,
        compressedWidth: 720,
        success: (cres) => this.uploadFile(cres.tempFilePath, resolve, reject),
        fail: () => this.uploadFile(filePath, resolve, reject)
      })
    })
  },

  uploadFile(filePath, resolve, reject) {
    const token = app.globalData.userInfo && app.globalData.userInfo.token
    wx.uploadFile({
      url: BASE_URL + '/api/v1/upload/image',
      filePath,
      name: 'file',
      header: { 'Authorization': token ? 'Bearer ' + token : '' },
      success: (res) => {
        try {
          const body = JSON.parse(res.data)
          if (body.code === 0 && body.data && body.data.url) {
            resolve(body.data.url)
          } else {
            wx.showToast({ title: body.msg || '上传失败', icon: 'none' })
            reject(body)
          }
        } catch (e) {
          reject(e)
        }
      },
      fail: reject
    })
  },

  onDeleteImage(e) {
    const index = e.currentTarget.dataset.index
    const images = this.data.images.slice()
    images.splice(index, 1)
    this.setData({ images })
  },

  onPreviewImage(e) {
    wx.previewImage({
      current: this.data.images[e.currentTarget.dataset.index],
      urls: this.data.images
    })
  },

  onSubmit() {
    if (this.data.submitting) {
      return
    }
    // 多校隔离：发布前先确认学校认证，避免填完表单才被后端拒绝
    const u = app.globalData.userInfo
    if (!u) {
      wx.showToast({ title: '请先登录', icon: 'none' })
      setTimeout(() => wx.navigateTo({ url: '/pages/login/login' }), 600)
      return
    }
    if (!u.certified) {
      wx.showModal({
        title: '需要先认证学校',
        content: '认证后才能发布拍品，拍品只对本校同学可见。',
        confirmText: '去认证',
        success: (r) => {
          if (r.confirm) wx.navigateTo({ url: '/packageUser/pages/school/school' })
        }
      })
      return
    }
    const err = this.validate()
    if (err) {
      wx.showToast({ title: err, icon: 'none' })
      return
    }

    this.setData({ submitting: true })
    // 元转分（取整），避免浮点精度问题
    const toFen = (yuan) => Math.round(parseFloat(yuan) * 100)

    request({
      url: '/api/v1/items',
      method: 'POST',
      data: {
        title: this.data.title.trim(),
        categoryId: this.data.categoryId,
        description: this.data.description,
        images: this.data.images,
        condition: this.data.condition,
        startPrice: toFen(this.data.startPriceYuan),
        increment: toFen(this.data.incrementYuan),
        durationHours: DURATIONS[this.data.durationIndex].hours
      }
    }).then(() => {
      wx.showModal({
        title: '提交成功',
        content: '拍品已进入待审核状态，审核通过后自动开拍',
        showCancel: false,
        success: () => wx.navigateTo({ url: '/packageUser/pages/my-items/my-items' })
      })
      this.resetForm()
    }).catch((e) => {
      wx.showToast({ title: e.msg || '提交失败', icon: 'none' })
    }).finally(() => {
      this.setData({ submitting: false })
    })
  },

  validate() {
    if (!this.data.title.trim()) {
      return '请填写标题'
    }
    if (!this.data.categoryId) {
      return '请选择分类'
    }
    if (!this.data.condition) {
      return '请选择成色'
    }
    const price = parseFloat(this.data.startPriceYuan)
    if (!price || price < 1) {
      return '起拍价至少 1 元'
    }
    const inc = parseFloat(this.data.incrementYuan)
    if (!inc || inc < 0.1) {
      return '加价幅度至少 0.1 元'
    }
    return null
  },

  resetForm() {
    this.setData({
      title: '', description: '', condition: '', images: [],
      startPriceYuan: '', incrementYuan: '1', suggest: null
    })
  },

  /** W5：AI 定价建议（大模型优先，后端自动降级规则估价） */
  onSuggestPrice() {
    if (this.data.suggesting) return
    if (!this.data.title.trim()) {
      wx.showToast({ title: '先填写标题再估价', icon: 'none' })
      return
    }
    if (!this.data.categoryId) {
      wx.showToast({ title: '先选择分类再估价', icon: 'none' })
      return
    }
    if (!this.data.condition) {
      wx.showToast({ title: '先选择成色再估价', icon: 'none' })
      return
    }
    this.setData({ suggesting: true })
    request({
      url: '/api/v1/items/price-suggest',
      method: 'POST',
      data: {
        title: this.data.title.trim(),
        categoryId: this.data.categoryId,
        condition: this.data.condition,
        description: this.data.description
      }
    }).then((data) => {
      this.setData({
        suggest: {
          minPrice: data.minPrice,
          maxPrice: data.maxPrice,
          minYuan: (data.minPrice / 100).toFixed(data.minPrice % 100 ? 2 : 0),
          maxYuan: (data.maxPrice / 100).toFixed(data.maxPrice % 100 ? 2 : 0),
          source: data.source,
          reason: data.reason,
          applied: false
        }
      })
    }).catch((e) => {
      wx.showToast({ title: e.msg || '估价失败，请稍后再试', icon: 'none' })
    }).finally(() => {
      this.setData({ suggesting: false })
    })
  },

  onApplySuggest() {
    this.setData({
      startPriceYuan: this.data.suggest.minYuan,
      'suggest.applied': true
    })
    wx.showToast({ title: '已填入起拍价', icon: 'success' })
  },

  onDismissSuggest() {
    this.setData({ suggest: null })
  }
})
