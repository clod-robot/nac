<template>
  <div class="portal-wrap">
    <el-card class="portal-card" v-loading="loading">
      <template #header><div class="title">{{ cfg.title || '网络准入认证' }}</div></template>
      <el-alert v-if="cfg.notice" :title="cfg.notice" type="info" :closable="false" style="margin-bottom:12px" />
      <template v-if="!done">
        <el-form label-width="0">
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
          <el-button type="primary" style="width:100%" @click="onAuth">认证入网</el-button>
        </el-form>
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
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getCaptcha, sendSms, portalConfig, portalAuth } from '../api/auth'

const router = useRouter()
const loading = ref(false)
const cfg = ref({})
const captchaImg = ref('')
const captchaKey = ref('')
const done = ref(false)
const cd = ref(0)
const ip = ref('')
const form = ref({ phone: '', captchaCode: '', code: '', mac: '' })

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
    const r = await portalAuth(form.value)
    localStorage.setItem('nac_portal_token', r.data)
    done.value = true
  } finally {
    loading.value = false
  }
}
onMounted(async () => {
  try {
    const r = await portalConfig()
    cfg.value = r.data || {}
  } catch (e) {}
  loadCaptcha()
})
</script>

<style scoped>
.portal-wrap { min-height: 100vh; display: flex; align-items: center; justify-content: center; background: #f0f2f5; padding: 20px; }
.portal-card { width: 380px; }
.title { font-size: 18px; font-weight: 600; text-align: center; }
.cap-row { display: flex; gap: 8px; width: 100%; }
.cap-row .el-input { flex: 1; }
.cap-img { height: 40px; cursor: pointer; border: 1px solid #dcdfe6; border-radius: 4px; }
.info { color: #606266; margin-bottom: 12px; }
</style>
