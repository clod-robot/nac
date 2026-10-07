import { useUserStore } from '../store/user'

// v-permission="['admin']"：角色不足时移除元素（前端兜底，最终以后端鉴权为准）
export const permission = {
  mounted(el, binding) {
    const store = useUserStore()
    const roles = binding.value
    if (Array.isArray(roles) && roles.length && !roles.includes(store.role)) {
      el.parentNode && el.parentNode.removeChild(el)
    }
  }
}
