<template>
  <el-container class="layout">
    <!-- 宽屏：固定侧边栏 -->
    <el-aside v-if="!isNarrow" width="210px" class="aside">
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
        <div class="header-left">
          <el-icon v-if="isNarrow" class="menu-btn" @click="drawer = true"><Menu /></el-icon>
          <span v-if="isNarrow" class="m-logo">NAC 准入控制</span>
        </div>
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

    <!-- 窄屏：抽屉式导航 -->
    <el-drawer v-model="drawer" :with-header="false" direction="ltr" size="210px" class="nav-drawer">
      <div class="logo">NAC 准入控制</div>
      <el-menu :default-active="$route.path" router background-color="#1f2d3d" text-color="#bfcbd9" active-text-color="#409EFF" @select="drawer = false">
        <el-menu-item index="/dashboard"><el-icon><Odometer /></el-icon><span>仪表盘</span></el-menu-item>
        <el-menu-item v-permission="['admin']" index="/portal-config"><el-icon><Setting /></el-icon><span>Portal 配置</span></el-menu-item>
        <el-menu-item v-permission="['admin']" index="/radius-user"><el-icon><Key /></el-icon><span>802.1X 口令</span></el-menu-item>
        <el-menu-item v-permission="['admin']" index="/op-log"><el-icon><Document /></el-icon><span>操作日志</span></el-menu-item>
        <el-menu-item index="/portal"><el-icon><Connection /></el-icon><span>终端认证</span></el-menu-item>
      </el-menu>
    </el-drawer>
  </el-container>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { Odometer, Setting, Document, Connection, ArrowDown, Key, Menu } from '@element-plus/icons-vue'
import { useUserStore } from '../store/user'
import { logout } from '../api/auth'
import { useBreakpoints } from '../composables/useBreakpoints'

const router = useRouter()
const store = useUserStore()
const { isNarrow } = useBreakpoints()
const drawer = ref(false)

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
.layout { height: 100vh; height: 100dvh; }
.aside { background: #1f2d3d; }
.logo { height: 60px; line-height: 60px; color: #fff; text-align: center; font-weight: 600; }
.header { display: flex; align-items: center; justify-content: space-between; background: #fff; border-bottom: 1px solid #eee; padding: 0 16px; }
.header-left { display: flex; align-items: center; gap: 10px; }
.menu-btn { font-size: 20px; cursor: pointer; color: #303133; }
.m-logo { font-weight: 600; color: #303133; }
.user { cursor: pointer; white-space: nowrap; }
</style>

<style>
/* el-drawer 渲染到 body，scoped 不生效，单独定义 */
.nav-drawer .el-drawer__body { padding: 0; background: #1f2d3d; }
</style>
