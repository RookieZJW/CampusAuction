// 学校认证：学生证信息 + 管理员审核（PENDING / APPROVED / REJECTED）
const { request } = require('../../../utils/request')
const imageUtil = require('../../../utils/image')
const app = getApp()

Page({
  data: {
    schools: [],
    schoolNames: [],
    selectedIndex: -1,
    status: 'NONE',
    certified: false,
    schoolName: '',
    realName: '',
    studentNo: '',
    studentCardUrl: '',
    studentCardPreview: '',
    rejectReason: '',
    loading: true,
    saving: false,
    previewVisible: false
  },

  onLoad() {
    this.loadSchools()
  },

  onPullDownRefresh() {
    this.loadMyAuth().finally(() => wx.stopPullDownRefresh())
  },

  loadSchools() {
    // 学校字典是公开接口，游客也能拉（认证页本身要求登录）
    request({ url: '/api/v1/schools', needAuth: false }).then((list) => {
      this.setData({
        schools: list || [],
        schoolNames: (list || []).map((s) => s.name)
      })
      return this.loadMyAuth()
    }).catch(() => {
      this.setData({ loading: false })
    })
  },

  loadMyAuth() {
    return request({ url: '/api/v1/schools/mine' }).then((info) => {
      const schools = this.data.schools
      const applySchoolId = info.applySchoolId || info.schoolId || null
      let selectedIndex = -1
      schools.forEach((s, index) => {
        if (s.id === applySchoolId) {
          selectedIndex = index
        }
      })
      this.setData({
        status: info.status || 'NONE',
        certified: !!info.certified,
        schoolName: info.schoolName || '',
        realName: info.realName || '',
        studentNo: info.studentNo || '',
        studentCardUrl: info.studentCard || '',
        studentCardPreview: info.studentCard || '',
        rejectReason: info.rejectReason || '',
        selectedIndex,
        loading: false
      })
      // 真机调试时 http 图片会被拦截，转 base64 才能预览
      if (info.studentCard) {
        imageUtil.displayUrl(info.studentCard).then((preview) => {
          if (preview) {
            this.setData({ studentCardPreview: preview })
          }
        })
      }
    }).catch(() => {
      this.setData({ loading: false })
    })
  },

  onSchoolChange(e) {
    this.setData({ selectedIndex: Number(e.detail.value) })
  },

  onRealNameInput(e) {
    this.setData({ realName: e.detail.value })
  },

  onStudentNoInput(e) {
    this.setData({ studentNo: e.detail.value })
  },

  onChooseCard() {
    wx.chooseMedia({
      count: 1,
      mediaType: ['image'],
      sourceType: ['album', 'camera'],
      success: (res) => {
        const file = res.tempFiles && res.tempFiles[0]
        if (!file) {
          return
        }
        wx.showLoading({ title: '上传中', mask: true })
        wx.compressImage({
          src: file.tempFilePath,
          quality: 85,
          compressedWidth: 1080,
          success: (cres) => this.uploadCard(cres.tempFilePath),
          fail: () => this.uploadCard(file.tempFilePath)
        })
      }
    })
  },

  uploadCard(filePath) {
    const userInfo = app.globalData.userInfo || wx.getStorageSync('userInfo') || {}
    wx.uploadFile({
      url: require('../../../utils/config').BASE_URL + '/api/v1/upload/image',
      filePath,
      name: 'file',
      header: { Authorization: userInfo.token ? 'Bearer ' + userInfo.token : '' },
      success: (res) => {
        wx.hideLoading()
        let body
        try {
          body = JSON.parse(res.data)
        } catch (e) {
          wx.showToast({ title: '上传返回异常', icon: 'none' })
          return
        }
        if (body.code === 0 && body.data && body.data.url) {
          this.setData({
            studentCardUrl: body.data.url,
            studentCardPreview: filePath
          })
          wx.showToast({ title: '学生证已上传', icon: 'success' })
        } else {
          wx.showToast({ title: body.msg || '上传失败', icon: 'none' })
        }
      },
      fail: () => {
        wx.hideLoading()
        wx.showToast({ title: '网络异常，上传失败', icon: 'none' })
      }
    })
  },

  onPreviewCard() {
    if (!this.data.studentCardPreview) {
      return
    }
    this.setData({ previewVisible: true })
  },

  onClosePreview() {
    this.setData({ previewVisible: false })
  },

  onSubmit() {
    if (this.data.saving) {
      return
    }
    const school = this.data.schools[this.data.selectedIndex]
    if (!school) {
      wx.showToast({ title: '请选择学校', icon: 'none' })
      return
    }
    if (!this.data.realName.trim()) {
      wx.showToast({ title: '请填写真实姓名', icon: 'none' })
      return
    }
    if (!this.data.studentNo.trim()) {
      wx.showToast({ title: '请填写学号', icon: 'none' })
      return
    }
    if (!this.data.studentCardUrl) {
      wx.showToast({ title: '请上传学生证照片', icon: 'none' })
      return
    }

    this.setData({ saving: true })
    request({
      url: '/api/v1/schools/auth',
      method: 'POST',
      data: {
        schoolId: school.id,
        realName: this.data.realName.trim(),
        studentNo: this.data.studentNo.trim(),
        studentCard: this.data.studentCardUrl
      }
    }).then((info) => {
      this.syncLogin(info)
      const status = info.status || 'PENDING'
      wx.showToast({
        title: status === 'PENDING' ? '已提交，等待审核' : '认证信息已更新',
        icon: 'success'
      })
      this.setData({
        status,
        certified: !!info.certified,
        schoolName: info.schoolName || '',
        rejectReason: ''
      })
      setTimeout(() => {
        const pages = getCurrentPages()
        if (pages.length > 1) {
          wx.navigateBack()
        } else {
          wx.switchTab({ url: '/pages/index/index' })
        }
      }, 900)
    }).catch((err) => {
      wx.showToast({ title: err.msg || '提交失败，请重试', icon: 'none' })
    }).finally(() => {
      this.setData({ saving: false })
    })
  },

  /** 同步本地登录态：首页列表 / 「我的」页据此切换本校拍场 */
  syncLogin(info) {
    const u = app.globalData.userInfo
    if (!u) {
      return
    }
    app.setLogin(Object.assign({}, u, {
      schoolId: info.schoolId,
      schoolName: info.shortName || info.schoolName || '',
      certified: !!info.certified,
      authStatus: info.status || 'PENDING'
    }))
  }
})
