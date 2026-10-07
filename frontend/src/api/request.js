import axios from 'axios'
import { ElMessage } from 'element-plus'
import qs from 'qs'

const request = axios.create({ baseURL: '/', timeout: 15000 })

// 请求拦截：携带 token；GET 参数用 qs 序列化
request.interceptors.request.use((cfg) => {
  const token = localStorage.getItem('nac_token')
  if (token) cfg.headers.Authorization = 'Bearer ' + token
  if (cfg.method === 'get') {
    cfg.paramsSerializer = (p) => qs.stringify(p, { arrayFormat: 'repeat' })
  }
  return cfg
})

// 响应拦截：统一业务码处理；401 清登录态
request.interceptors.response.use(
  (resp) => {
    const d = resp.data
    if (d && d.code === 200) return d
    if (d && d.code === 401) {
      localStorage.clear()
      location.href = '/login'
    }
    ElMessage.error((d && d.message) || '请求失败')
    return Promise.reject(d)
  },
  (err) => {
    ElMessage.error('网络异常，请稍后重试')
    return Promise.reject(err)
  }
)

export default request
