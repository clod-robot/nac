<template>
  <div class="page">
    <el-card class="card" shadow="never">
      <template #header><span class="card-title">NTP 服务器</span></template>
      <el-form label-width="110px">
        <el-form-item label="当前服务器">
          <span>{{ ntp }}</span>
          <el-tag size="small" type="info" style="margin-left:10px">时区：{{ tz }}</el-tag>
        </el-form-item>
        <el-form-item label="NTP 地址">
          <el-input v-model="ntpInput" placeholder="如 ntp.aliyun.com 或 192.168.1.1" clearable style="max-width:340px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="savingNtp" @click="saveNtp">保存并立即同步</el-button>
          <span class="tip">写入 systemd-timesyncd，保存后立即生效。</span>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="card" shadow="never">
      <template #header><span class="card-title">EAP-TLS 分片大小</span></template>
      <el-form label-width="130px">
        <el-form-item label="默认值">
          <el-tag>{{ frag.default }}</el-tag>
        </el-form-item>
        <el-form-item label="建议值">
          <span class="tip">{{ frag.recommend }}</span>
        </el-form-item>
        <el-form-item label="合法区间">
          <span>{{ frag.min }} ~ {{ frag.max }} 字节</span>
        </el-form-item>
        <el-form-item label="当前值">
          <el-tag type="success" size="large">{{ frag.current }}</el-tag>
          <span v-if="frag.changed" class="changed">已修改为 {{ frag.current }}（热生效）</span>
        </el-form-item>
        <el-form-item label="修改为">
          <el-input-number v-model="fragInput" :min="frag.min" :max="frag.max" :step="20" />
          <el-button type="primary" style="margin-left:12px" :loading="savingFrag" @click="saveFrag">保存</el-button>
        </el-form-item>
        <el-form-item>
          <span class="tip">越大握手往返越少、认证越快；若接入老旧交换机出现认证失败/分片被丢弃，请调小到 512 或 240。</span>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="card" shadow="never">
      <template #header>
        <span class="card-title">802.1X CA 证书</span>
        <el-button size="small" style="float:right" @click="downloadCa">下载当前 CA 证书</el-button>
      </template>
      <el-descriptions :column="2" border size="small" class="desc">
        <el-descriptions-item label="CA 主体">{{ ca.caSubject }}</el-descriptions-item>
        <el-descriptions-item label="CA 到期">{{ ca.caNotAfter }}</el-descriptions-item>
        <el-descriptions-item label="CA 算法">{{ ca.caKeyAlg }} / {{ ca.caSigAlg }}</el-descriptions-item>
        <el-descriptions-item label="服务端证书">{{ ca.serverKeyAlg }} / {{ ca.serverSigAlg }}（约 {{ ca.serverCertSize }} 字节）</el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">导入客户自带 CA（将用其重签服务端证书，热生效）</el-divider>
      <el-form label-width="130px">
        <el-form-item label="CA 证书(.pem)">
          <el-upload :auto-upload="false" :limit="1" :on-change="(f) => caCertFile = f.raw" :on-remove="() => caCertFile = null">
            <el-button>选择证书文件</el-button>
          </el-upload>
        </el-form-item>
        <el-form-item label="CA 私钥(.pem)">
          <el-upload :auto-upload="false" :limit="1" :on-change="(f) => caKeyFile = f.raw" :on-remove="() => caKeyFile = null">
            <el-button>选择私钥文件</el-button>
          </el-upload>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="uploading" @click="uploadCa">校验并应用</el-button>
          <span class="tip">服务端将校验：证书可解析、为 CA、私钥匹配、能签发服务端证书。任一项不通过将弹窗提示且不覆盖现有证书。</span>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="card" shadow="never">
      <template #header><span class="card-title">其他参数</span></template>
      <el-empty description="更多参数（认证端口、会话时长、VLAN 等）后续开放，敬请期待" :image-size="80" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getSystemInfo, setSystemNtp, getRadiusConfig, setRadiusFragment, uploadRadiusCa, downloadRadiusCa } from '../api/auth'

const ntp = ref('-')
const tz = ref('-')
const ntpInput = ref('')
const savingNtp = ref(false)

const frag = reactive({ current: 1020, default: 1020, min: 128, max: 1400, recommend: '', changed: false })
const fragInput = ref(1020)
const savingFrag = ref(false)

const ca = reactive({ caSubject: '', caNotAfter: '', caKeyAlg: '', caSigAlg: '', serverKeyAlg: '', serverSigAlg: '', serverCertSize: 0 })
const caCertFile = ref(null)
const caKeyFile = ref(null)
const uploading = ref(false)

async function loadAll() {
  try {
    const s = await getSystemInfo()
    ntp.value = s.data.ntpServer || '-'
    tz.value = s.data.timeZone || '-'
    ntpInput.value = ntp.value && ntp.value !== '-' ? ntp.value.split(/\s/)[0] : ''
  } catch (e) {}
  try {
    const r = await getRadiusConfig()
    const f = r.data.fragment
    Object.assign(frag, { current: f.current, default: f.default, min: f.min, max: f.max, recommend: f.recommend })
    fragInput.value = f.current
    Object.assign(ca, r.data.ca)
  } catch (e) {}
}

async function saveNtp() {
  savingNtp.value = true
  try {
    await setSystemNtp({ ntp: ntpInput.value })
    ElMessage.success('NTP 已更新并同步')
    loadAll()
  } catch (e) {
    ElMessage.error('修改失败：' + (e?.response?.data?.message || e.message))
  } finally { savingNtp.value = false }
}

async function saveFrag() {
  savingFrag.value = true
  try {
    await setRadiusFragment({ size: fragInput.value })
    frag.current = fragInput.value
    frag.changed = true
    ElMessage.success('分片大小已更新，新认证立即生效')
  } catch (e) {
    ElMessage.error('修改失败：' + (e?.response?.data?.message || e.message))
  } finally { savingFrag.value = false }
}

async function uploadCa() {
  if (!caCertFile.value) { ElMessage.warning('请选择 CA 证书文件'); return }
  if (!caKeyFile.value) { ElMessage.warning('请选择 CA 私钥文件'); return }
  uploading.value = true
  const fd = new FormData()
  fd.append('caCert', caCertFile.value)
  fd.append('caKey', caKeyFile.value)
  try {
    const r = await uploadRadiusCa(fd)
    Object.assign(ca, r.data)
    ElMessage.success('CA 证书校验通过并已生效，服务端证书已重签')
  } catch (e) {
    ElMessage.error('CA 不可用，已回退：' + (e?.response?.data?.message || e.message))
  } finally { uploading.value = false }
}

async function downloadCa() {
  try {
    const blob = await downloadRadiusCa()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url; a.download = 'nac-ca.pem'; a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    ElMessage.error('下载失败：' + (e?.response?.data?.message || e.message))
  }
}

onMounted(loadAll)
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 16px; }
.card-title { font-weight: 600; }
.tip { color: #909399; font-size: 12px; margin-left: 8px; }
.changed { color: #67c23a; font-size: 13px; margin-left: 10px; }
.desc { margin-bottom: 8px; }
</style>
