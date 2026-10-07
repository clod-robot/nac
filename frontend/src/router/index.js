import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '../store/user'

const routes = [
  { path: '/login', component: () => import('../views/Login.vue'), meta: { public: true } },
  { path: '/portal', component: () => import('../views/PortalAuth.vue'), meta: { public: true } },
  {
    path: '/',
    component: () => import('../views/Layout.vue'),
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', component: () => import('../views/Dashboard.vue') },
      { path: 'user', component: () => import('../views/UserManage.vue'), meta: { roles: ['admin'] } },
      { path: 'portal-config', component: () => import('../views/PortalConfig.vue'), meta: { roles: ['admin'] } },
      { path: 'sms-gateway', component: () => import('../views/SmsGateway.vue'), meta: { roles: ['admin'] } },
      { path: 'nas', component: () => import('../views/NasManage.vue'), meta: { roles: ['admin'] } },
      { path: 'op-log', component: () => import('../views/OpLog.vue'), meta: { roles: ['admin'] } },
      { path: 'auth-log', component: () => import('../views/AuthLog.vue'), meta: { roles: ['admin'] } },
      { path: 'online', component: () => import('../views/OnlineManage.vue'), meta: { roles: ['admin'] } },
      { path: 'terminal', component: () => import('../views/Terminal.vue'), meta: { roles: ['admin'] } }
    ]
  }
]

const router = createRouter({
  history: createWebHistory('/nac/'),
  routes
})

// 路由守卫：未登录跳转登录；角色不足拦截（防水平越权前端兜底）
router.beforeEach((to) => {
  const store = useUserStore()
  if (to.meta.public) return true
  if (!store.token) return { path: '/login', query: { redirect: to.fullPath } }
  if (to.meta.roles && !to.meta.roles.includes(store.role)) {
    return { path: '/dashboard' }
  }
  return true
})

export default router
