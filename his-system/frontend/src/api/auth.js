import request from '@/utils/request'

export function getCaptcha() {
  return request.get('/api/auth/captcha')
}

export function login(data) {
  return request.post('/api/auth/login', data)
}

export function logout() {
  return request.post('/api/auth/logout')
}

export function getCurrentUser() {
  return request.get('/api/auth/current')
}
