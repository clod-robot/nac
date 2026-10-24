<template>
  <div class="login-wrap">
    <el-card class="login-card" :class="{ shake }">
      <template #header><div class="title">NAC 准入控制系统</div></template>
      <el-tabs v-model="tab" @tab-change="onTabChange">
        <el-tab-pane label="账号登录" name="pwd">
          <el-form :model="form" label-width="0" @keyup.enter="onLogin">
            <el-form-item>
              <el-input v-model="form.username" placeholder="用户名" :prefix-icon="User" />
            </el-form-item>
            <el-form-item>
              <el-input v-model="form.password" type="password" placeholder="密码" :prefix-icon="Lock" show-password />
            </el-form-item>
            <el-form-item>
              <div class="cap-row">
                <el-input v-model="form.captchaCode" placeholder="图形验证码" />
                <div class="cap-box" @click="loadPwdCaptcha" title="点击刷新">
                  <img v-if="pwdCaptchaImg" :key="pwdCaptchaImg" :src="pwdCaptchaImg" class="cap-img" />
                  <div v-if="pwdCapExpired" class="cap-mask">验证码已过期<br />点击刷新</div>
                </div>
              </div>
            </el-form-item>
            <el-button type="primary" :loading="loading" style="width:100%" @click="onLogin">登 录</el-button>
          </el-form>
        </el-tab-pane>
        <el-tab-pane label="短信登录" name="sms">
          <el-form label-width="0">
            <el-form-item>
              <el-input v-model="sms.phone" placeholder="手机号" />
            </el-form-item>
            <el-form-item>
              <div class="cap-row">
                <el-input v-model="sms.captchaCode" placeholder="图形验证码" />
                <div class="cap-box" @click="loadSmsCaptcha" title="点击刷新">
                  <img v-if="smsCaptchaImg" :key="smsCaptchaImg" :src="smsCaptchaImg" class="cap-img" />
                  <div v-if="smsCapExpired" class="cap-mask">验证码已过期<br />点击刷新</div>
                </div>
              </div>
            </el-form-item>
            <el-form-item>
              <div class="cap-row">
                <el-input v-model="sms.code" placeholder="短信验证码" />
                <el-button :disabled="cd > 0" @click="onSendSms">{{ cd > 0 ? cd + 's' : '获取验证码' }}</el-button>
              </div>
            </el-form-item>
            <el-button type="primary" :loading="loading" style="width:100%" @click="onSmsLogin">登 录</el-button>
          </el-form>
        </el-tab-pane>
      </el-tabs>
      <div class="tip">默认管理员 admin / admin123（首次登录后请修改）</div>
      <div class="dl-row">
        <el-link type="primary" :underline="false" href="/downloads/8021x-client.zip" download>
          <el-icon style="vertical-align:-2px;margin-right:4px"><Download /></el-icon>下载 802.1X 客户端
        </el-link>
        <span class="dl-tip">（含与服务器一致的 CA/客户端证书，p12 口令 123456）</span>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { User, Lock, Download } from '@element-plus/icons-vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getCaptcha, login, sendSms, smsLogin, getSecretStatus } from '../api/auth'
import { useUserStore } from '../store/user'

const router = useRouter()
const route = useRoute()
const store = useUserStore()

// 仅允许站内相对路径作为登录后跳转目标，拒绝外域/协议相对 URL，防范开放重定向
function safeRedirect(target) {
  if (typeof target !== 'string' || !target) return '/dashboard'
  if (!target.startsWith('/') || target.startsWith('//') || target.startsWith('/\\')) return '/dashboard'
  if (/[\\]/.test(target)) return '/dashboard'
  return target
}
const tab = ref('pwd')
const loading = ref(false)
const form = ref({ username: '', password: '', captchaCode: '' })
const sms = ref({ phone: '', captchaCode: '', code: '' })
const cd = ref(0)

// 登录失败时的轻微抖动反馈：先移除再添加，保证连续失败也能重新触发动画
const shake = ref(false)
let shakeTimer = null
async function triggerShake() {
  shake.value = false
  await nextTick()
  shake.value = true
  clearTimeout(shakeTimer)
  shakeTimer = setTimeout(() => { shake.value = false }, 450)
}

// 与后端 Redis 验证码 TTL(300s) 一致：到期后图片加灰色遮罩提醒过期，点击刷新
const CAPTCHA_TTL_MS = 5 * 60 * 1000

// 账号登录 Tab 独立验证码状态（切换 Tab 时强制刷新，不复用旧 key）
const pwdCaptchaImg = ref('')
const pwdCaptchaKey = ref('')
const pwdCapExpired = ref(false)
let pwdCapLoadedAt = 0
let pwdCapTimer = null

// 短信登录 Tab 独立验证码状态
const smsCaptchaImg = ref('')
const smsCaptchaKey = ref('')
const smsCapExpired = ref(false)
let smsCapLoadedAt = 0
let smsCapTimer = null

function clearTimer(which) {
  if (which === 'pwd' && pwdCapTimer) { clearInterval(pwdCapTimer); pwdCapTimer = null }
  if (which === 'sms' && smsCapTimer) { clearInterval(smsCapTimer); smsCapTimer = null }
}

async function loadPwdCaptcha() {
  const r = await getCaptcha()
  pwdCaptchaKey.value = r.data.captchaKey
  pwdCaptchaImg.value = r.data.captchaImg
  pwdCapExpired.value = false
  pwdCapLoadedAt = Date.now()
  clearTimer('pwd')
  pwdCapTimer = setInterval(() => {
    if (Date.now() - pwdCapLoadedAt >= CAPTCHA_TTL_MS) {
      pwdCapExpired.value = true
      clearTimer('pwd')
    }
  }, 1000)
}

async function loadSmsCaptcha() {
  const r = await getCaptcha()
  smsCaptchaKey.value = r.data.captchaKey
  smsCaptchaImg.value = r.data.captchaImg
  smsCapExpired.value = false
  smsCapLoadedAt = Date.now()
  clearTimer('sms')
  smsCapTimer = setInterval(() => {
    if (Date.now() - smsCapLoadedAt >= CAPTCHA_TTL_MS) {
      smsCapExpired.value = true
      clearTimer('sms')
    }
  }, 1000)
}

// 切换到新 Tab 时总是拉取一张全新验证码：保证「每处验证码独立获取，不复用前一 Tab」
function onTabChange(name) {
  if (name === 'pwd') loadPwdCaptcha()
  else if (name === 'sms') loadSmsCaptcha()
}

onBeforeUnmount(() => { clearTimer('pwd'); clearTimer('sms') })

async function onLogin() {
  loading.value = true
  let ok = false
  try {
    const r = await login({ ...form.value, captchaKey: pwdCaptchaKey.value })
    store.setLogin(r.data)
    ok = true
    ElMessage.success('登录成功')
    if (store.isAdmin()) {
      try {
        const st = await getSecretStatus()
        if (st.data.isDefault) {
          ElMessage.warning('网关内部签名密钥仍为出厂默认值，已引导至「系统参数设置」，请立即修改')
          router.replace('/system-config')
          return
        }
      } catch (e) {}
    }
    router.replace(safeRedirect(route.query.redirect))
  } finally {
    loading.value = false
    // 登录失败时自动刷新验证码：后端验证码一次性销毁，旧 key 已无法再用
    if (!ok) { loadPwdCaptcha(); triggerShake() }
  }
}
async function onSendSms() {
  try {
    await sendSms({ phone: sms.value.phone, captchaKey: smsCaptchaKey.value, captchaCode: sms.value.captchaCode, bizType: 'login' })
    ElMessage.success('验证码已发送')
    cd.value = 60
    const t = setInterval(() => { cd.value--; if (cd.value <= 0) clearInterval(t) }, 1000)
  } catch (e) {
    // 发送失败（多为图形验证码错误或过期）自动刷新，避免用户手点
    loadSmsCaptcha()
    triggerShake()
  }
}
async function onSmsLogin() {
  loading.value = true
  let ok = false
  try {
    const r = await smsLogin({ phone: sms.value.phone, code: sms.value.code })
    store.setLogin(r.data)
    ok = true
    ElMessage.success('登录成功')
    router.replace('/dashboard')
  } finally {
    loading.value = false
    // 短信登录失败自动刷新图形验证码，用户重试时无需手点
    if (!ok) { loadSmsCaptcha(); triggerShake() }
  }
}
onMounted(loadPwdCaptcha)
</script>

<style scoped>
.login-wrap { height: 100vh; height: 100dvh; display: flex; align-items: center; justify-content: center; background: #1f2d3d; padding: 16px; }
.login-card { width: min(380px, 100%); }
.title { font-size: 18px; font-weight: 600; text-align: center; }
.cap-row { display: flex; gap: 8px; width: 100%; align-items: center; }
.cap-row .el-input { flex: 1; min-width: 0; }
/* 按图片原始高度 1:1 显示，避免浏览器下采样把字符渲染发虚（灰蒙蒙） */
.cap-box { position: relative; flex-shrink: 0; cursor: pointer; border: 1px solid #dcdfe6; border-radius: 4px; overflow: hidden; line-height: 0; perspective: 600px; }
.cap-img { height: 48px; display: block; animation: capIn .4s cubic-bezier(.22,.61,.36,1) both; }
/* 过期遮罩：5 分钟未提交登录，后端验证码已过期，灰蒙蒙提示点击刷新 */
.cap-mask { position: absolute; inset: 0; background: rgba(0, 0, 0, 0.45); color: #fff; font-size: 12px; line-height: 1.5; display: flex; align-items: center; justify-content: center; text-align: center; }
.tip { margin-top: 12px; color: #909399; font-size: 12px; text-align: center; }
.dl-row { margin-top: 10px; text-align: center; font-size: 12px; }
.dl-tip { color: #909399; margin-left: 6px; }

/* Tab 切换：内容淡入上浮，切回时重播；指示条带回弹滑动 */
.login-card :deep(.el-tab-pane.is-active) { animation: tabIn .34s cubic-bezier(.22,.61,.36,1) both; }
.login-card :deep(.el-tabs__active-bar) { transition: transform .36s cubic-bezier(.34,1.3,.64,1), width .36s cubic-bezier(.34,1.3,.64,1); }
.login-card :deep(.el-tabs__item) { transition: color .25s ease; }
.login-card :deep(.el-tabs__item:hover) { color: #409EFF; }

/* 登录失败：卡片轻微抖动；验证码刷新：3D 翻转进入 */
.shake { animation: cardShake .45s ease; }
.cap-mask { animation: fadeIn .25s ease; }

@keyframes tabIn {
  from { opacity: 0; transform: translateY(10px) scale(.985); }
  to { opacity: 1; transform: none; }
}
@keyframes capIn {
  from { opacity: .2; transform: rotateY(-75deg) scale(.9); }
  to { opacity: 1; transform: none; }
}
@keyframes cardShake {
  0%, 100% { transform: translateX(0); }
  15% { transform: translateX(-6px); }
  30% { transform: translateX(5px); }
  45% { transform: translateX(-4px); }
  60% { transform: translateX(3px); }
  75% { transform: translateX(-2px); }
}
@keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }
@media (prefers-reduced-motion: reduce) {
  .shake, .cap-img, .cap-mask, .login-card :deep(.el-tab-pane.is-active) { animation: none !important; }
  .login-card :deep(.el-tabs__active-bar) { transition: none !important; }
}
</style>
