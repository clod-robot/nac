import { defineStore } from 'pinia'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('nac_token') || '',
    username: localStorage.getItem('nac_username') || '',
    role: localStorage.getItem('nac_role') || '',
    userId: Number(localStorage.getItem('nac_user_id')) || null,
    mustChangePwd: localStorage.getItem('nac_must_change_pwd') === '1'
  }),
  actions: {
    setLogin(data) {
      this.token = data.token
      this.username = data.username
      this.role = data.role
      this.userId = data.userId || null
      this.mustChangePwd = !!data.mustChangePwd
      localStorage.setItem('nac_token', data.token)
      localStorage.setItem('nac_username', data.username)
      localStorage.setItem('nac_role', data.role)
      if (data.userId != null) localStorage.setItem('nac_user_id', String(data.userId))
      if (this.mustChangePwd) localStorage.setItem('nac_must_change_pwd', '1')
      else localStorage.removeItem('nac_must_change_pwd')
    },
    clearMustChangePwd() {
      this.mustChangePwd = false
      localStorage.removeItem('nac_must_change_pwd')
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
      localStorage.removeItem('nac_token')
      localStorage.removeItem('nac_username')
      localStorage.removeItem('nac_role')
      localStorage.removeItem('nac_user_id')
      localStorage.removeItem('nac_must_change_pwd')
    }
  }
})
