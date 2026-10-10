<template>
  <el-container class="layout">
    <!-- 宽屏：固定侧边栏 -->
    <el-aside v-if="!isNarrow" width="210px" class="aside">
      <div class="logo">NAC 准入控制</div>
      <el-menu :default-active="$route.path" router background-color="#1f2d3d" text-color="#bfcbd9" active-text-color="#409EFF">
        <el-menu-item index="/dashboard"><el-icon><Odometer /></el-icon><span>仪表盘</span></el-menu-item>
        <el-menu-item v-permission="['admin']" index="/user"><el-icon><User /></el-icon><span>账号管理</span></el-menu-item>
        <el-menu-item v-permission="['admin']" index="/exempt-terminals"><el-icon><Key /></el-icon><span>免认证终端</span></el-menu-item>
        <el-menu-item v-permission="['admin']" index="/portal-config"><el-icon><Setting /></el-icon><span>Portal 配置</span></el-menu-item>
        <el-menu-item v-permission="['admin']" index="/system-config"><el-icon><Tools /></el-icon><span>参数配置</span></el-menu-item>
        <el-menu-item v-permission="['admin']" index="/sms-gateway"><el-icon><Message /></el-icon><span>短信网关</span></el-menu-item>
        <el-menu-item v-permission="['admin']" index="/network"><el-icon><Connection /></el-icon><span>网络管理</span></el-menu-item>
        <el-menu-item v-permission="['admin']" index="/nas"><el-icon><Key /></el-icon><span>NAS管理</span></el-menu-item>
        <el-menu-item v-permission="['admin']" index="/op-log"><el-icon><Document /></el-icon><span>操作日志</span></el-menu-item>
        <el-menu-item v-permission="['admin']" index="/auth-log"><el-icon><Tickets /></el-icon><span>认证日志</span></el-menu-item>
        <el-menu-item v-permission="['admin']" index="/syslog"><el-icon><Share /></el-icon><span>日志管理</span></el-menu-item>
        <el-menu-item v-permission="['admin']" index="/online"><el-icon><Monitor /></el-icon><span>在线终端</span></el-menu-item>
        <el-menu-item v-permission="['admin']" index="/terminal"><el-icon><Connection /></el-icon><span>远程终端</span></el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="header-left">
          <el-icon v-if="isNarrow" class="menu-btn" @click="drawer = true"><Menu /></el-icon>
          <span v-if="isNarrow" class="m-logo">NAC 准入控制</span>
        </div>
        <div class="header-right">
          <el-tooltip content="服务器时间（点击同步）" placement="bottom">
            <span class="sys-clock" @click="refresh">{{ clock }}</span>
          </el-tooltip>
          <el-tooltip :content="`NTP 服务器：${ntp}`" placement="bottom">
            <span class="sys-ntp" @click="openNtp">
              <el-icon><Clock /></el-icon><span class="ntp-text">NTP：{{ ntp }}</span><el-icon><Edit /></el-icon>
            </span>
          </el-tooltip>
          <el-dropdown @command="onCmd">
            <span class="user">{{ store.username }}（{{ store.role }}）<el-icon><ArrowDown /></el-icon></span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main><router-view /></el-main>
    </el-container>

    <!-- NTP 修改弹窗 -->
    <el-dialog v-model="ntpDlg" title="修改 NTP 服务器" width="420px">
      <el-input v-model="ntpInput" placeholder="如 ntp.aliyun.com 或 192.168.1.1" clearable @keyup.enter="saveNtp" />
      <div class="ntp-tip">当前时区：{{ tz }}。保存后将写入 systemd-timesyncd 并立即同步。</div>
      <template #footer>
        <el-button @click="ntpDlg = false">取消</el-button>
        <el-button type="primary" @click="saveNtp">保存</el-button>
      </template>
    </el-dialog>

    <!-- 窄屏：抽屉式导航 -->
    <el-drawer v-model="drawer" :with-header="false" direction="ltr" size="210px" class="nav-drawer">
      <div class="logo">NAC 准入控制</div>
      <el-menu :default-active="$route.path" router background-color="#1f2d3d" text-color="#bfcbd9" active-text-color="#409EFF" @select="drawer = false">
        <el-menu-item index="/dashboard"><el-icon><Odometer /></el-icon><span>仪表盘</span></el-menu-item>
        <el-menu-item v-permission="['admin']" index="/user"><el-icon><User /></el-icon><span>账号管理</span></el-menu-item>
        <el-menu-item v-permission="['admin']" index="/exempt-terminals"><el-icon><Key /></el-icon><span>免认证终端</span></el-menu-item>
        <el-menu-item v-permission="['admin']" index="/portal-config"><el-icon><Setting /></el-icon><span>Portal 配置</span></el-menu-item>
        <el-menu-item v-permission="['admin']" index="/system-config"><el-icon><Tools /></el-icon><span>参数配置</span></el-menu-item>
        <el-menu-item v-permission="['admin']" index="/sms-gateway"><el-icon><Message /></el-icon><span>短信网关</span></el-menu-item>
        <el-menu-item v-permission="['admin']" index="/network"><el-icon><Connection /></el-icon><span>网络管理</span></el-menu-item>
        <el-menu-item v-permission="['admin']" index="/nas"><el-icon><Key /></el-icon><span>NAS管理</span></el-menu-item>
        <el-menu-item v-permission="['admin']" index="/op-log"><el-icon><Document /></el-icon><span>操作日志</span></el-menu-item>
        <el-menu-item v-permission="['admin']" index="/auth-log"><el-icon><Tickets /></el-icon><span>认证日志</span></el-menu-item>
        <el-menu-item v-permission="['admin']" index="/syslog"><el-icon><Share /></el-icon><span>日志管理</span></el-menu-item>
        <el-menu-item v-permission="['admin']" index="/online"><el-icon><Monitor /></el-icon><span>在线终端</span></el-menu-item>
        <el-menu-item v-permission="['admin']" index="/terminal"><el-icon><Connection /></el-icon><span>远程终端</span></el-menu-item>
      </el-menu>
    </el-drawer>
    <!-- 强制修改默认密码：仍为 admin123 时阻断使用，关闭即登出 -->
    <el-dialog v-model="forcePwdVisible" title="首次登录：设置管理员归属并修改默认密码" width="440px"
      :close-on-click-modal="false" :show-close="false" :close-on-press-escape="false"
      @close="onForceClose">
      <el-alert type="warning" :closable="false" show-icon style="margin-bottom:14px"
        title="检测到您仍在使用默认密码 admin123。请先设置该管理员账号归属哪个部门、哪个人使用，并修改密码后再进入系统。" />
      <el-form label-width="100px">
        <el-form-item label="当前账号"><el-input :model-value="store.username" disabled /></el-form-item>
        <el-form-item label="归属部门" required>
          <el-input v-model="adminDept" placeholder="如：信息中心" />
        </el-form-item>
        <el-form-item label="使用人(姓名)" required>
          <el-input v-model="adminRealName" placeholder="请填写实际领用人姓名" />
        </el-form-item>
        <el-form-item label="联系电话" required>
          <el-input v-model="adminPhone" placeholder="用于短信登录/找回密码" maxlength="15" />
        </el-form-item>
        <el-form-item label="新密码"><el-input v-model="newPwd" type="password" show-password placeholder="至少 6 位，请勿再用 admin123" /></el-form-item>
        <el-form-item label="确认密码"><el-input v-model="newPwd2" type="password" show-password placeholder="再次输入新密码" @keyup.enter="submitForcePwd" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" :loading="forceSaving" @click="submitForcePwd">确认并进入系统</el-button>
      </template>
    </el-dialog>
  </el-container>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import { Odometer, Setting, Tools, Message, Document, Tickets, Monitor, Connection, ArrowDown, Key, Menu, User, Share, Clock, Edit } from '@element-plus/icons-vue'
import { useUserStore } from '../store/user'
import { logout, getSystemInfo, setSystemNtp, userResetPassword, userUpdateProfile } from '../api/auth'
import { useBreakpoints } from '../composables/useBreakpoints'
import { useIdleTimeout } from '../composables/useIdleTimeout'

const router = useRouter()
const store = useUserStore()
const { isNarrow } = useBreakpoints()
const drawer = ref(false)

// 强制修改默认密码
const forcePwdVisible = ref(false)
const newPwd = ref('')
const newPwd2 = ref('')
const forceSaving = ref(false)
const adminDept = ref('')
const adminRealName = ref('')
const adminPhone = ref('')

// 服务器时间（按服务器时区实时走时）与 NTP
const clock = ref('--')
const ntp = ref('-')
const tz = ref('UTC')
const ntpDlg = ref(false)
const ntpInput = ref('')
let offsetMs = 0
let tickTimer = null
let syncTimer = null

function fmtServer(ms) {
  const f = new Intl.DateTimeFormat('en-GB', {
    timeZone: tz.value || 'UTC',
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23'
  })
  const p = {}
  for (const part of f.formatToParts(new Date(ms))) p[part.type] = part.value
  return `${p.year}-${p.month}-${p.day} ${p.hour}:${p.minute}:${p.second}`
}
async function refresh() {
  try {
    const r = await getSystemInfo()
    const d = r.data
    offsetMs = (d.epochMs || Date.now()) - Date.now()
    ntp.value = d.ntpServer || '-'
    tz.value = d.timeZone || 'UTC'
    clock.value = fmtServer(Date.now() + offsetMs)
  } catch (e) { /* 未登录或接口不可用时静默 */ }
}
function openNtp() { ntpInput.value = ntp.value && ntp.value !== '-' ? ntp.value.split(/\s/)[0] : ''; ntpDlg.value = true }
async function saveNtp() {
  try {
    await setSystemNtp({ ntp: ntpInput.value })
    ElMessage.success('NTP 已更新')
    ntpDlg.value = false
    refresh()
  } catch (e) { ElMessage.error('修改失败：' + (e?.response?.data?.message || e.message)) }
}

onMounted(() => {
  refresh()
  tickTimer = setInterval(() => { clock.value = fmtServer(Date.now() + offsetMs) }, 1000)
  syncTimer = setInterval(refresh, 60000) // 每分钟与服务器对齐，修正漂移
  // 仍为默认密码则强制改密
  if (store.mustChangePwd) { forcePwdVisible.value = true }
})
onUnmounted(() => { clearInterval(tickTimer); clearInterval(syncTimer) })

async function submitForcePwd() {
  if (!adminDept.value.trim()) { ElMessage.warning('请填写归属部门'); return }
  if (!adminRealName.value.trim()) { ElMessage.warning('请填写使用人(姓名)'); return }
  if (!newPwd.value || newPwd.value.length < 6) { ElMessage.warning('新密码至少 6 位'); return }
  if (newPwd.value === 'admin123') { ElMessage.warning('不能继续使用默认密码 admin123'); return }
  if (newPwd.value !== newPwd2.value) { ElMessage.warning('两次输入的密码不一致'); return }
  if (!adminPhone.value.trim()) { ElMessage.warning('请填写联系电话'); return }
  if (!/^\d{6,15}$/.test(adminPhone.value.trim())) { ElMessage.warning('联系电话格式不正确'); return }
  forceSaving.value = true
  try {
    // 1) 设置管理员账号归属（部门 + 使用人 + 联系电话）
    await userUpdateProfile({ id: store.userId, dept: adminDept.value.trim(), realName: adminRealName.value.trim(), phone: adminPhone.value.trim() || undefined })
    // 2) 修改默认密码
    await userResetPassword({ id: store.userId, password: newPwd.value })
    store.clearMustChangePwd()
    ElMessage.success('已设置归属并修改密码，请妥善保管')
    forcePwdVisible.value = false
  } catch (e) {
    ElMessage.error('保存失败：' + (e?.response?.data?.message || e.message))
  } finally { forceSaving.value = false }
}
function onForceClose() {
  // 未完成改密而关闭弹窗 → 强制登出
  if (store.mustChangePwd) {
    store.logout()
    router.replace('/login')
  }
}

// 30 分钟无操作自动退出系统
useIdleTimeout(async () => {
  try { await logout() } catch (e) {}
  store.logout()
  ElMessage.warning('长时间无操作，已自动退出系统')
  router.replace('/login')
})

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
.header { display: flex; align-items: center; justify-content: space-between; background: #fff; border-bottom: 1px solid #eee; padding: 0 16px; gap: 8px; }
.header-left { display: flex; align-items: center; gap: 10px; min-width: 0; }
.header-right { display: flex; align-items: center; gap: 18px; flex-wrap: wrap; justify-content: flex-end; }
.sys-clock { font-variant-numeric: tabular-nums; font-weight: 600; color: #303133; cursor: pointer; letter-spacing: .5px; white-space: nowrap; }
.sys-ntp { display: inline-flex; align-items: center; gap: 4px; font-size: 13px; color: #606266; cursor: pointer; white-space: nowrap; }
.sys-ntp:hover { color: #409EFF; }
.ntp-tip { color: #909399; font-size: 12px; margin-top: 8px; }
.menu-btn { font-size: 20px; cursor: pointer; color: #303133; }
.m-logo { font-weight: 600; color: #303133; }
.user { cursor: pointer; white-space: nowrap; }
/* 窄屏精简：<=640 隐藏时钟/仅留 NTP 图标，避免页头溢出 */
@media (max-width: 640px) {
  .header { padding: 6px 10px; }
  .header-right { gap: 10px; }
  .sys-clock { display: none; }
  .sys-ntp .ntp-text { display: none; }
  .sys-ntp { font-size: 0; }
  .m-logo { font-size: 15px; }
}
/* 强制改密遮罩：未改密前不允许操作页面 */
.must-change-mask { position: fixed; inset: 0; z-index: 3000; display: flex; align-items: center; justify-content: center; background: rgba(0,0,0,.45); }
</style>

<style>
/* el-drawer 渲染到 body，scoped 不生效，单独定义 */
.nav-drawer .el-drawer__body { padding: 0; background: #1f2d3d; }
</style>
