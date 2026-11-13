import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'

// axios 实例：统一携带管理员 token、解包 Result、处理鉴权失败
const request = axios.create({
  baseURL: '/api/v1',
  timeout: 10000
})

request.interceptors.request.use((config) => {
  const token = localStorage.getItem('admin_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && body.code === 0) {
      return body.data
    }
    const message = body?.msg || body?.message || '请求失败'
    ElMessage.error(message)
    return Promise.reject(new Error(message))
  },
  (error) => {
    if (error.response && error.response.status === 401) {
      localStorage.removeItem('admin_token')
      ElMessage.error('登录已过期，请重新登录')
      router.push({ name: 'Login' })
    } else {
      const body = error.response?.data
      ElMessage.error(body?.msg || body?.message || error.message || '网络异常')
    }
    return Promise.reject(error)
  }
)

export default request
