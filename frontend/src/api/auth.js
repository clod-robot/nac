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

// NAS 管理（admin）：共享密钥 + NAS 设备台账
export const getNasSecret = () => request.get('/api/auth/nas/secret')
export const setNasSecret = (data) => request.put('/api/auth/nas/secret', data)
export const listNas = () => request.get('/api/auth/nas/list')
export const renameNas = (data) => request.put('/api/auth/nas/name', data)

// 账号管理（admin）
export const userList = (params) => request.get('/api/auth/admin/users/list', { params })
export const userCreate = (data) => request.post('/api/auth/admin/users', data)
export const userUpdateStatus = (data) => request.put('/api/auth/admin/users/status', data)
export const userUpdateLimit = (data) => request.put('/api/auth/admin/users/terminal-limit', data)
export const userUpdateDept = (data) => request.put('/api/auth/admin/users/dept', data)
export const userUpdateAuthMethod = (data) => request.put('/api/auth/admin/users/auth-method', data)
export const userUpdateProfile = (data) => request.put('/api/auth/admin/users/profile', data)
export const userResetPassword = (data) => request.put('/api/auth/admin/users/password', data)
export const userDelete = (id) => request.delete(`/api/auth/admin/users/${id}`)
// 批量导入：下载模板(CSV，带BOM) / 上传导入
export const userDownloadTemplate = () => request.get('/api/auth/admin/users/template', { responseType: 'blob' })
export const userImport = (formData) => request.post('/api/auth/admin/users/import', formData, {
  headers: { 'Content-Type': 'multipart/form-data' }, timeout: 60000
})

// 仪表盘服务器监控
export const getMonitorSnapshot = () => request.get('/api/auth/monitor/snapshot')
export const getMonitorStats = () => request.get('/api/auth/monitor/stats')

// 短信网关配置（admin）
export const getSmsConfig = () => request.get('/api/auth/sms/admin/config')
export const setSmsConfig = (data) => request.put('/api/auth/sms/admin/config', data)

// 网络管理（admin）
export const getNetworkInterfaces = () => request.get('/api/auth/network/interfaces')
export const applyNetwork = (data) => request.post('/api/auth/network/apply', data)

// 日志管理 - syslog 外发（admin）
export const getSyslogConfig = () => request.get('/api/auth/syslog/config')
export const saveSyslogConfig = (data) => request.put('/api/auth/syslog/config', data)
export const testSyslog = () => request.post('/api/auth/syslog/test')

// 免认证终端（admin）
export const exemptList = () => request.get('/api/auth/admin/exempt-terminals')
export const exemptCreate = (data) => request.post('/api/auth/admin/exempt-terminals', data)
export const exemptUpdate = (data) => request.put('/api/auth/admin/exempt-terminals', data)
export const exemptDelete = (id) => request.delete(`/api/auth/admin/exempt-terminals/${id}`)

// 系统信息：服务器时间 / NTP（admin）
export const getSystemInfo = () => request.get('/api/auth/system/info')
export const setSystemNtp = (data) => request.put('/api/auth/system/ntp', data)

// RADIUS 参数配置（admin）：分片大小 / CA 证书，热生效
export const getRadiusConfig = () => request.get('/api/radius/config')
export const setRadiusFragment = (data) => request.put('/api/radius/config/fragment', data)
export const uploadRadiusCa = (formData) => request.post('/api/radius/config/ca', formData, {
  headers: { 'Content-Type': 'multipart/form-data' }, timeout: 60000
})
export const downloadRadiusCa = () => request.get('/api/radius/config/ca.pem', { responseType: 'blob' })
