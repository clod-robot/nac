// 权限占位：路由守卫已在 router 中实现，此文件保留用于扩展按钮级权限指令
import Vue from 'vue'

// v-permission 指令：按角色控制按钮显隐（前端越权防护第一层，后端接口二次校验）
Vue.directive('permission', {
  inserted(el, binding) {
    const roles = binding.value
    const role = JSON.parse(localStorage.getItem('his_user') || '{}').role
    if (roles && roles.length && !roles.includes(role)) {
      el.parentNode && el.parentNode.removeChild(el)
    }
  }
})
