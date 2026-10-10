<template>
  <div class="portal-wrap" :style="wrapStyle">
    <el-card class="portal-card" v-loading="loading">
      <template #header>
        <div class="brand">
          <img v-if="cfg.logo" :src="cfg.logo" class="brand-logo" />
          <span v-else class="brand-ph">{{ cfg.siteName || 'NAC' }}</span>
          <span v-if="cfg.siteName" class="brand-site">{{ cfg.siteName }}</span>
        </div>
        <div class="title">{{ cfg.title || '网络准入认证' }}</div>
      </template>
      <el-alert v-if="cfg.notice && !blocked" :title="cfg.notice" type="info" :closable="false" style="margin-bottom:12px" />
      <template v-if="blocked">
        <el-result icon="warning" title="非法访问" sub-title="防伪校验未通过：重定向链接无效或已过期，请从合法 Portal 入口重新进入。">
        </el-result>
      </template>
      <template v-else-if="!done">
        <div v-if="authType === 'both'" class="auth-tabs">
          <span class="auth-tab" :class="{ on: activeAuth === 'sms' }" @click="activeAuth = 'sms'">短信认证</span>
          <span class="auth-tab" :class="{ on: activeAuth === 'account' }" @click="activeAuth = 'account'">账号认证</span>
        </div>
        <el-form label-width="0">
          <template v-if="showSms">
            <el-form-item><el-input v-model="form.phone" placeholder="手机号" /></el-form-item>
            <el-form-item>
              <div class="cap-row">
                <el-input v-model="form.captchaCode" placeholder="图形验证码" />
                <img v-if="captchaImg" :src="captchaImg" class="cap-img" @click="loadCaptcha" />
              </div>
            </el-form-item>
            <el-form-item>
              <div class="cap-row">
                <el-input v-model="form.code" placeholder="短信验证码" />
                <el-button :disabled="cd > 0" @click="onSend">{{ cd > 0 ? cd + 's' : '获取验证码' }}</el-button>
              </div>
            </el-form-item>
          </template>
          <template v-else>
            <el-form-item><el-input v-model="form.username" placeholder="账号" /></el-form-item>
            <el-form-item><el-input v-model="form.password" type="password" show-password placeholder="密码" @keyup.enter="onAuth" /></el-form-item>
          </template>
          <el-button type="primary" style="width:100%" @click="onAuth">{{ cfg.buttonText || '认证入网' }}</el-button>
        </el-form>
        <div v-if="cfg.copyright" class="copy">{{ cfg.copyright }}</div>
      </template>
      <template v-else>
        <el-result icon="success" title="认证成功" sub-title="您已通过准入认证，可以访问网络">
          <template #extra>
            <div class="info">IP：{{ ip }}　MAC：{{ form.mac || '-' }}</div>
            <el-button type="primary" @click="$router.push('/dashboard')">进入系统</el-button>
          </template>
        </el-result>
      </template>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getCaptcha, sendSms, portalConfig, portalAuth, portalVerify } from '../api/auth'

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const cfg = ref({})
// 品牌定制背景：图片或纯色（来自 Portal 定制页面配置）
const wrapStyle = computed(() => {
  if (String(cfg.value.bgType) === 'image' && cfg.value.bgImage) {
    return { backgroundImage: `url(${cfg.value.bgImage})`, backgroundSize: 'cover', backgroundPosition: 'center' }
  }
  return { background: cfg.value.bgColor || '#f0f2f5' }
})
const captchaImg = ref('')
const captchaKey = ref('')
const done = ref(false)
const blocked = ref(false)
const cd = ref(0)
const ip = ref('')
const form = ref({ phone: '', captchaCode: '', code: '', username: '', password: '', mac: '', ts: '', sign: '' })
const activeAuth = ref('sms')
const authType = computed(() => cfg.value.portalAuthType || 'sms')
const showSms = computed(() => authType.value !== 'account' && (authType.value !== 'both' || activeAuth.value === 'sms'))

async function loadCaptcha() {
  const r = await getCaptcha()
  captchaKey.value = r.data.captchaKey
  captchaImg.value = r.data.captchaImg
}
async function onSend() {
  await sendSms({ phone: form.value.phone, captchaKey: captchaKey.value, captchaCode: form.value.captchaCode, bizType: 'portal' })
  ElMessage.success('验证码已发送')
  cd.value = 60
  const t = setInterval(() => { cd.value--; if (cd.value <= 0) clearInterval(t) }, 1000)
}
async function onAuth() {
  loading.value = true
  try {
    let payload
    if (activeAuth.value === 'account') {
      payload = { type: 'account', username: form.value.username, password: form.value.password,
        mac: form.value.mac, ts: form.value.ts, sign: form.value.sign }
    } else {
      payload = { ...form.value }
    }
    const r = await portalAuth(payload)
    localStorage.setItem('nac_portal_token', r.data)
    done.value = true
  } finally {
    loading.value = false
  }
}
onMounted(async () => {
  // 取重定向 URL 里的防伪参数（ts/mac/sign）
  const q = route.query
  form.value.ts = q.ts ? String(q.ts) : ''
  form.value.sign = q.sign ? String(q.sign) : ''
  if (q.mac) form.value.mac = String(q.mac)
  try {
    const r = await portalConfig()
    cfg.value = r.data || {}
  } catch (e) {}
  // 防伪推：开启后必须验签通过，否则拦截
  if (String(cfg.value.antiForgeryEnabled) === 'true') {
    try {
      const v = await portalVerify({ ts: form.value.ts, mac: form.value.mac, sign: form.value.sign })
      if (v.data !== true) { blocked.value = true; return }
    } catch (e) { blocked.value = true; return }
  }
  if (authType.value !== 'account') loadCaptcha()
})
</script>

<style scoped>
.portal-wrap { min-height: 100vh; display: flex; align-items: center; justify-content: center; background: #f0f2f5; padding: 20px; }
.portal-card { width: 380px; }
.title { font-size: 18px; font-weight: 600; text-align: center; }
.auth-tabs { display: flex; gap: 8px; margin-bottom: 12px; }
.auth-tab { flex: 1; text-align: center; padding: 7px 0; border-radius: 4px; background: #f0f2f5; color: #606266; font-size: 13px; cursor: pointer; }
.auth-tab.on { background: #409eff; color: #fff; font-weight: 600; }
.cap-row { display: flex; gap: 8px; width: 100%; }
.cap-row .el-input { flex: 1; }
.cap-img { height: 40px; cursor: pointer; border: 1px solid #dcdfe6; border-radius: 4px; }
.info { color: #606266; margin-bottom: 12px; }
.brand { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.brand-logo { height: 24px; max-width: 140px; object-fit: contain; }
.brand-ph { font-size: 12px; color: #909399; border: 1px dashed #c0c4cc; padding: 1px 8px; border-radius: 3px; }
.brand-site { font-size: 13px; color: #606266; font-weight: 600; }
.copy { text-align: center; font-size: 11px; color: #909399; margin-top: 14px; line-height: 1.5; }
</style>
