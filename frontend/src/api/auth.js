import request from './request'

// 认证
export const getCaptcha = () => request.get('/api/auth/captcha')
export const login = (data) => request.post('/api/auth/login', data)
export const logout = () => request.post('/api/auth/logout')

// 短信
export const sendSms = (data) => request.post('/api/auth/sms/send', data)
export const smsLogin = (data) => request.post('/api/auth/sms/login', data)

// Portal
export const portalConfig = () => request.get('/api/portal/config')
export const portalAuth = (data) => request.post('/api/portal/auth', data)
export const portalAdminConfig = () => request.get('/api/portal/admin/config')
export const updatePortalConfig = (data) => request.put('/api/portal/admin/config', data)

// 日志
export const logList = (params) => request.get('/api/log/list', { params })
export const authLogList = (params) => request.get('/api/log/auth-list', { params })
export const onlineList = (params) => request.get('/api/log/online-list', { params })
export const onlineDelete = (id) => request.delete(`/api/log/online/${id}`)

// RADIUS 802.1X 口令管理（admin）
export const getRadiusStatus = (username) => request.get('/api/auth/radius/password', { params: { username } })
export const setRadiusPassword = (data) => request.post('/api/auth/radius/password', data)
export const clearRadiusPassword = (username) => request.delete('/api/auth/radius/password', { params: { username } })

// 账号管理（admin）
export const userList = (params) => request.get('/api/auth/admin/users/list', { params })
export const userCreate = (data) => request.post('/api/auth/admin/users', data)
export const userUpdateStatus = (data) => request.put('/api/auth/admin/users/status', data)
export const userUpdateLimit = (data) => request.put('/api/auth/admin/users/terminal-limit', data)
export const userUpdateDept = (data) => request.put('/api/auth/admin/users/dept', data)
export const userResetPassword = (data) => request.put('/api/auth/admin/users/password', data)
export const userDelete = (id) => request.delete(`/api/auth/admin/users/${id}`)
