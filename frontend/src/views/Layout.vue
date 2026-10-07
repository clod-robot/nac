<template>
  <el-container class="layout">
    <el-aside width="210px" class="aside">
      <div class="logo">NAC 准入控制</div>
      <el-menu :default-active="$route.path" router background-color="#1f2d3d" text-color="#bfcbd9" active-text-color="#409EFF">
        <el-menu-item index="/dashboard"><el-icon><Odometer /></el-icon><span>仪表盘</span></el-menu-item>
        <el-menu-item v-permission="['admin']" index="/portal-config"><el-icon><Setting /></el-icon><span>Portal 配置</span></el-menu-item>
        <el-menu-item v-permission="['admin']" index="/radius-user"><el-icon><Key /></el-icon><span>802.1X 口令</span></el-menu-item>
        <el-menu-item v-permission="['admin']" index="/op-log"><el-icon><Document /></el-icon><span>操作日志</span></el-menu-item>
        <el-menu-item index="/portal"><el-icon><Connection /></el-icon><span>终端认证</span></el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <div></div>
        <el-dropdown @command="onCmd">
          <span class="user">{{ store.username }}（{{ store.role }}）<el-icon><ArrowDown /></el-icon></span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>
      <el-main><router-view /></el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { Odometer, Setting, Document, Connection, ArrowDown, Key } from '@element-plus/icons-vue'
import { useUserStore } from '../store/user'
import { logout } from '../api/auth'

const router = useRouter()
const store = useUserStore()
async function onCmd(c) {
  if (c === 'logout') {
    await ElMessageBox.confirm('确认退出登录？', '提示', { type: 'warning' })
    try { await logout() } catch (e) {}
    store.logout()
    router.replace('/login')
  }
}
</script>

<style scoped>
.layout { height: 100vh; }
.aside { background: #1f2d3d; }
.logo { height: 60px; line-height: 60px; color: #fff; text-align: center; font-weight: 600; }
.header { display: flex; align-items: center; justify-content: space-between; background: #fff; border-bottom: 1px solid #eee; }
.user { cursor: pointer; }
</style>
