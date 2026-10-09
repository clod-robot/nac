<template>
  <div class="login-wrap">
    <el-card class="login-card">
      <template #header><div class="title">NAC 准入控制系统</div></template>
      <el-tabs v-model="tab">
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
                <img v-if="captchaImg" :src="captchaImg" class="cap-img" @click="loadCaptcha" title="点击刷新" />
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
                <img v-if="captchaImg" :src="captchaImg" class="cap-img" @click="loadCaptcha" />
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
import { ref, onMounted } from 'vue'
import { User, Lock, Download } from '@element-plus/icons-vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getCaptcha, login, sendSms, smsLogin } from '../api/auth'
import { useUserStore } from '../store/user'

const router = useRouter()
const route = useRoute()
const store = useUserStore()
const tab = ref('pwd')
const loading = ref(false)
const captchaImg = ref('')
const captchaKey = ref('')
const form = ref({ username: '', password: '', captchaCode: '' })
const sms = ref({ phone: '', captchaCode: '', code: '' })
const cd = ref(0)

async function loadCaptcha() {
  const r = await getCaptcha()
  captchaKey.value = r.data.captchaKey
  captchaImg.value = r.data.captchaImg
}
async function onLogin() {
  loading.value = true
  try {
    const r = await login({ ...form.value, captchaKey: captchaKey.value })
    store.setLogin(r.data)
    ElMessage.success('登录成功')
    router.replace(route.query.redirect || '/dashboard')
  } finally {
    loading.value = false
    loadCaptcha()
  }
}
async function onSendSms() {
  await sendSms({ phone: sms.value.phone, captchaKey: captchaKey.value, captchaCode: sms.value.captchaCode, bizType: 'login' })
  ElMessage.success('验证码已发送')
  cd.value = 60
  const t = setInterval(() => { cd.value--; if (cd.value <= 0) clearInterval(t) }, 1000)
}
async function onSmsLogin() {
  loading.value = true
  try {
    const r = await smsLogin({ phone: sms.value.phone, code: sms.value.code })
    store.setLogin(r.data)
    ElMessage.success('登录成功')
    router.replace('/dashboard')
  } finally {
    loading.value = false
  }
}
onMounted(loadCaptcha)
</script>

<style scoped>
.login-wrap { height: 100vh; height: 100dvh; display: flex; align-items: center; justify-content: center; background: #1f2d3d; padding: 16px; }
.login-card { width: min(380px, 100%); }
.title { font-size: 18px; font-weight: 600; text-align: center; }
.cap-row { display: flex; gap: 8px; width: 100%; align-items: center; }
.cap-row .el-input { flex: 1; min-width: 0; }
.cap-img { height: 40px; cursor: pointer; border: 1px solid #dcdfe6; border-radius: 4px; flex-shrink: 0; }
.tip { margin-top: 12px; color: #909399; font-size: 12px; text-align: center; }
.dl-row { margin-top: 10px; text-align: center; font-size: 12px; }
.dl-tip { color: #909399; margin-left: 6px; }
</style>
