import Vue from 'vue'
import Vuex from 'vuex'

Vue.use(Vuex)

// token 存 localStorage（按项目约定：token 存储在浏览器本地）
export default new Vuex.Store({
  state: {
    token: localStorage.getItem('his_token') || '',
    user: JSON.parse(localStorage.getItem('his_user') || 'null')
  },
  mutations: {
    SET_TOKEN(state, token) {
      state.token = token
      localStorage.setItem('his_token', token)
    },
    SET_USER(state, user) {
      state.user = user
      localStorage.setItem('his_user', JSON.stringify(user))
    },
    LOGOUT(state) {
      state.token = ''
      state.user = null
      localStorage.removeItem('his_token')
      localStorage.removeItem('his_user')
    }
  }
})
