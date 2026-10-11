import axios from 'axios'
import { ElMessage } from 'element-plus'
import qs from 'qs'
import { useUserStore } from '../store/user'

// 从内存 store 读取 token（pinia 未就绪时降级为空，不抛错）
function getToken() {
  try { return useUserStore().token || '' } catch (e) { return '' }
}

// 读取非 HttpOnly 的 XSRF-TOKEN cookie，用于 CSRF 双提交校验
function readCookie(name) {
  const m = document.cookie.match(new RegExp('(?:^|; )' + name + '=([^;]*)'))
  return m ? decodeURIComponent(m[1]) : ''
}

const request = axios.create({ baseURL: '/', timeout: 15000, withCredentials: true })

// 请求拦截：携带 token；回传 X-XSRF-TOKEN；GET 参数用 qs 序列化
request.interceptors.request.use((cfg) => {
  const token = getToken()
  if (token) cfg.headers.Authorization = 'Bearer ' + token
  const xsrf = readCookie('XSRF-TOKEN')
  if (xsrf) cfg.headers['X-XSRF-TOKEN'] = xsrf
  if (cfg.method === 'get') {
    cfg.paramsSerializer = (p) => qs.stringify(p, { arrayFormat: 'repeat' })
  }
  return cfg
})

// 响应拦截：统一业务码处理；401 清登录态
request.interceptors.response.use(
  (resp) => {
    // 文件下载（blob）直接返回原始响应，不走业务码解析
    if (resp.config && resp.config.responseType === 'blob') return resp
    const d = resp.data
    if (d && d.code === 200) return d
    if (d && d.code === 401) {
      try { useUserStore().logout() } catch (e) {}
      location.href = '/login'
    }
    ElMessage.error((d && d.message) || '请求失败')
    return Promise.reject(d)
  },
  (err) => {
    // 服务重启/不可达时给出友好的降级提示，避免笼统“网络异常”或页面白屏
    const status = err && err.response ? err.response.status : 0
    const url = (err && err.config && err.config.url) || ''
    if (status === 502 || status === 503 || status === 504) {
      ElMessage({ type: 'warning', message: '服务正在升级维护，稍后即可恢复（' + url + '）', duration: 3000 })
    } else if (!err.response) {
      // 连接被拒绝 / 网络中断：通常是目标服务正在重启
      ElMessage({ type: 'warning', message: '服务暂时不可用，请稍后重试（' + url + '）', duration: 3000 })
    } else {
      ElMessage.error('请求失败（' + status + '）')
    }
    return Promise.reject(err)
  }
)

export default request
