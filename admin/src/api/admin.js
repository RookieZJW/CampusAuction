import request from './request'

// 管理后台全部接口封装（对应 server AdminController / AdminManageController）
export const adminApi = {
  // 管理员登录
  login(data) {
    return request.post('/admin/login', data)
  },
  // 数据看板
  stats() {
    return request.get('/admin/stats')
  },
  // 用户管理
  users(params) {
    return request.get('/admin/users', { params })
  },
  banUser(userId) {
    return request.post(`/admin/users/${userId}/ban`)
  },
  unbanUser(userId) {
    return request.post(`/admin/users/${userId}/unban`)
  },
  // 拍卖监控
  auctions(params) {
    return request.get('/admin/auctions', { params })
  },
  // 订单查询
  orders(params) {
    return request.get('/admin/orders', { params })
  },
  // 举报处理（W5）
  reports(params) {
    return request.get('/admin/reports', { params })
  },
  handleReport(reportId, data) {
    return request.post(`/admin/reports/${reportId}/handle`, data)
  },
  // 拍品审核
  auditItems() {
    return request.get('/admin/audit/items')
  },
  approveItem(itemId) {
    return request.post(`/admin/audit/items/${itemId}/approve`)
  },
  rejectItem(itemId, data) {
    return request.post(`/admin/audit/items/${itemId}/reject`, data)
  },
  // 学生证认证审核
  schoolAuths() {
    return request.get('/admin/school-auths')
  },
  approveSchoolAuth(id) {
    return request.post(`/admin/school-auths/${id}/approve`)
  },
  rejectSchoolAuth(id, data) {
    return request.post(`/admin/school-auths/${id}/reject`, data)
  }
}
