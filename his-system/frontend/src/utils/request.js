import axios from 'axios'
import { Message } from 'element-ui'
import store from '@/store'
import router from '@/router'

// axios 实例：baseURL 走 devServer 代理到网关
const service = axios.create({
  baseURL: '/',
  timeout: 15000
})

// 请求拦截：附加 token
service.interceptors.request.use(config => {
  const token = store.state.token
  if (token) {
    config.headers['Authorization'] = 'Bearer ' + token
  }
  return config
}, error => Promise.reject(error))

// 响应拦截：统一处理 Result 结构 + 401 跳登录
service.interceptors.response.use(
  response => {
    const res = response.data
    if (res.code !== undefined && res.code !== 200) {
      Message.error(res.message || '请求失败')
      if (res.code === 401) {
        store.commit('LOGOUT')
        router.push('/login')
      }
      return Promise.reject(new Error(res.message || 'Error'))
    }
    return res
  },
  error => {
    const status = error.response ? error.response.status : 0
    if (status === 401) {
      Message.error('登录已过期，请重新登录')
      store.commit('LOGOUT')
      router.push('/login')
    } else if (status === 429) {
      Message.error('请求过于频繁，请稍后再试')
    } else {
      Message.error(error.message || '网络异常')
    }
    return Promise.reject(error)
  }
)

export default service
