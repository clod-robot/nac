import Vue from 'vue'
import VueRouter from 'vue-router'
import store from '@/store'

Vue.use(VueRouter)

const routes = [
  { path: '/login', name: 'Login', component: () => import('@/views/Login.vue') },
  {
    path: '/',
    component: () => import('@/layout/Layout.vue'),
    redirect: '/home',
    children: [
      { path: 'home', name: 'Home', component: () => import('@/views/Home.vue'), meta: { title: '工作台' } },
      { path: 'pharmacy/prescriptions', name: 'PrescriptionList', component: () => import('@/views/pharmacy/PrescriptionList.vue'), meta: { title: '待发药处方', roles: ['pharmacist', 'admin'] } },
      { path: 'pharmacy/stats', name: 'PharmacyStats', component: () => import('@/views/pharmacy/Stats.vue'), meta: { title: '发药统计', roles: ['pharmacist', 'admin'] } },
      { path: 'pharmacy/drugs', name: 'DrugList', component: () => import('@/views/pharmacy/DrugList.vue'), meta: { title: '药品目录' } },
      { path: 'patient/list', name: 'PatientList', component: () => import('@/views/patient/PatientList.vue'), meta: { title: '患者档案' } },
      { path: 'emr/list', name: 'EmrList', component: () => import('@/views/emr/EmrList.vue'), meta: { title: '电子病历', roles: ['doctor', 'admin'] } }
    ]
  }
]

const router = new VueRouter({ routes })

// 全局前置守卫：未登录跳登录页；简单角色控制（越权防护前端层，后端仍需校验）
router.beforeEach((to, from, next) => {
  const token = store.state.token
  if (to.path === '/login') {
    next()
  } else if (!token) {
    next('/login')
  } else {
    const roles = to.meta.roles
    const userRole = store.state.user ? store.state.user.role : ''
    if (roles && roles.length && !roles.includes(userRole)) {
      // 无页面权限：无提示静默跳回首页，避免枚举式探测
      next('/home')
    } else {
      next()
    }
  }
})

export default router
