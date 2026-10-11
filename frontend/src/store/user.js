import { defineStore } from 'pinia'

// 清理历史版本遗留的持久化凭据（旧版曾将 token 写入 localStorage）
function clearLegacyStorage() {
  try {
    localStorage.removeItem('nac_token')
    localStorage.removeItem('nac_username')
    localStorage.removeItem('nac_role')
    localStorage.removeItem('nac_user_id')
    localStorage.removeItem('nac_must_change_pwd')
  } catch (e) { /* 隐私模式等场景忽略 */ }
}
// 模块加载即清除一次，确保升级后旧的落盘 token 被抹除
clearLegacyStorage()

export const useUserStore = defineStore('user', {
  state: () => ({
    token: '',
    username: '',
    role: '',
    userId: null,
    mustChangePwd: false
  }),
  actions: {
    setLogin(data) {
      this.token = ''
      this.username = data.username
      this.role = data.role
      this.userId = data.userId || null
      this.mustChangePwd = !!data.mustChangePwd
      // 会话凭据由 HttpOnly Cookie 承载，前端不持有 token；此处仅存身份用于 UI/路由
      clearLegacyStorage()
    },
    clearMustChangePwd() {
      this.mustChangePwd = false
    },
    isAdmin() {
      return this.role === 'admin'
    },
    logout() {
      this.token = ''
      this.username = ''
      this.role = ''
      this.userId = null
      this.mustChangePwd = false
      clearLegacyStorage()
    }
  }
})
