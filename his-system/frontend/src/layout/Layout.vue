<template>
  <el-container style="height:100%;">
    <el-aside width="220px" class="sidebar">
      <div class="logo">HIS 医院系统</div>
      <el-menu :default-active="$route.path" router background-color="#001f3f" text-color="#fff" active-text-color="#409EFF">
        <el-menu-item index="/home"><i class="el-icon-s-home" /><span>工作台</span></el-menu-item>
        <el-submenu v-if="hasRole(['pharmacist','admin'])" index="pharmacy">
          <template slot="title"><i class="el-icon-first-aid-kit" /><span>药房管理</span></template>
          <el-menu-item index="/pharmacy/prescriptions">待发药处方</el-menu-item>
          <el-menu-item index="/pharmacy/stats">发药统计</el-menu-item>
          <el-menu-item index="/pharmacy/drugs">药品目录</el-menu-item>
        </el-submenu>
        <el-menu-item index="/patient/list"><i class="el-icon-user" /><span>患者档案</span></el-menu-item>
        <el-menu-item v-if="hasRole(['doctor','admin'])" index="/emr/list"><i class="el-icon-document" /><span>电子病历</span></el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <span class="page-title">{{ $route.meta.title }}</span>
        <el-dropdown @command="handleCommand" style="float:right;">
          <span class="user-info">
            <i class="el-icon-user-solid" /> {{ user ? user.realName : '' }}（{{ roleLabel }}）
            <i class="el-icon-arrow-down el-icon--right" />
          </span>
          <el-dropdown-menu slot="dropdown">
            <el-dropdown-item command="logout">退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </el-dropdown>
      </el-header>
      <el-main class="main-content">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script>
import store from '@/store'
import { logout } from '@/api/auth'
import router from '@/router'

export default {
  name: 'Layout',
  computed: {
    user() { return store.state.user },
    roleLabel() {
      const map = { admin: '管理员', doctor: '医生', pharmacist: '药师', nurse: '护士' }
      return (this.user && map[this.user.role]) || ''
    }
  },
  methods: {
    hasRole(roles) {
      return this.user && roles.includes(this.user.role)
    },
    async handleCommand(cmd) {
      if (cmd === 'logout') {
        try { await logout() } catch (e) { /* ignore */ }
        store.commit('LOGOUT')
        router.push('/login')
      }
    }
  }
}
</script>

<style scoped>
.sidebar { background: #001f3f; }
.logo { height: 60px; line-height: 60px; text-align: center; color: #fff; font-size: 18px; font-weight: bold; background: #001529; }
.header { background: #fff; border-bottom: 1px solid #e6e6e6; display: flex; align-items: center; }
.page-title { font-size: 16px; font-weight: bold; }
.user-info { cursor: pointer; color: #333; }
.main-content { background: #f0f2f5; padding: 16px; }
</style>
