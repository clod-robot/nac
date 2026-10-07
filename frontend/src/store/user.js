import { defineStore } from 'pinia'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('nac_token') || '',
    username: localStorage.getItem('nac_username') || '',
    role: localStorage.getItem('nac_role') || ''
  }),
  actions: {
    setLogin(data) {
      this.token = data.token
      this.username = data.username
      this.role = data.role
      localStorage.setItem('nac_token', data.token)
      localStorage.setItem('nac_username', data.username)
      localStorage.setItem('nac_role', data.role)
    },
    isAdmin() {
      return this.role === 'admin'
    },
    logout() {
      this.token = ''
      this.username = ''
      this.role = ''
      localStorage.removeItem('nac_token')
      localStorage.removeItem('nac_username')
      localStorage.removeItem('nac_role')
    }
  }
})
