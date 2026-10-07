<template>
  <el-card shadow="never">
    <template #header>
      <div style="display:flex;justify-content:space-between;align-items:center">
        <span>日志管理 · Syslog 外发</span>
        <el-tag :type="form.enabled ? 'success' : 'info'" size="small">{{ form.enabled ? '外发已启用' : '外发未启用' }}</el-tag>
      </div>
    </template>

    <el-alert type="info" :closable="false" style="margin-bottom:16px">
      将<strong>操作日志</strong>与<strong>认证日志</strong>按 syslog(RFC3164) 报文实时外发到指定服务器，便于集中审计与合规留存。配置保存后即时生效。
    </el-alert>

    <el-form :model="form" label-width="140px" style="max-width:640px" v-loading="loading">
      <el-form-item label="启用外发">
        <el-switch v-model="form.enabled" />
      </el-form-item>
      <el-form-item label="服务器地址">
        <el-input v-model="form.host" placeholder="如 192.168.10.200 或 syslog.example.com" :disabled="!form.enabled" />
      </el-form-item>
      <el-form-item label="端口">
        <el-input-number v-model="form.port" :min="1" :max="65535" :disabled="!form.enabled" />
        <span class="tip">默认 514</span>
      </el-form-item>
      <el-form-item label="协议">
        <el-select v-model="form.protocol" style="width:160px" :disabled="!form.enabled">
          <el-option label="UDP" value="UDP" />
          <el-option label="TCP" value="TCP" />
        </el-select>
      </el-form-item>
      <el-form-item label="Facility">
        <el-select v-model="form.facility" style="width:220px">
          <el-option v-for="f in facilities" :key="f.v" :label="f.label" :value="f.v" />
        </el-select>
      </el-form-item>
      <el-form-item label="应用标识">
        <el-input v-model="form.appName" placeholder="nac" />
      </el-form-item>
      <el-form-item label="外发操作日志">
        <el-switch v-model="form.forwardOpLog" />
      </el-form-item>
      <el-form-item label="外发认证日志">
        <el-switch v-model="form.forwardAuthLog" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="onSave" :loading="saving">保存配置</el-button>
        <el-button @click="onTest" :loading="testing">测试连接</el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getSyslogConfig, saveSyslogConfig, testSyslog } from '../api/auth'

const loading = ref(false)
const saving = ref(false)
const testing = ref(false)

const facilities = [
  { v: 0, label: '0 kern' }, { v: 1, label: '1 user' }, { v: 3, label: '3 daemon' },
  { v: 4, label: '4 auth' }, { v: 10, label: '10 authpriv' },
  { v: 16, label: '16 local0' }, { v: 17, label: '17 local1' }, { v: 18, label: '18 local2' },
  { v: 19, label: '19 local3' }, { v: 20, label: '20 local4' }, { v: 21, label: '21 local5' },
  { v: 22, label: '22 local6' }, { v: 23, label: '23 local7' }
]

const form = reactive({
  enabled: false, host: '', port: 514, protocol: 'UDP',
  facility: 16, appName: 'nac', forwardOpLog: true, forwardAuthLog: true
})

async function load() {
  loading.value = true
  try {
    const r = await getSyslogConfig()
    Object.assign(form, r.data || {})
  } finally {
    loading.value = false
  }
}

async function onSave() {
  saving.value = true
  try {
    await saveSyslogConfig({ ...form })
    ElMessage.success('配置已保存并即时生效')
  } finally {
    saving.value = false
  }
}

async function onTest() {
  if (!form.enabled) { ElMessage.warning('请先启用外发并填写服务器地址'); return }
  testing.value = true
  try {
    // 先保存，再用生效配置发测试报文
    await saveSyslogConfig({ ...form })
    const r = await testSyslog()
    if (r.data && r.data.success) ElMessage.success('测试报文发送成功，请在 syslog 服务器侧确认接收')
    else ElMessage.error('测试发送失败，请检查地址/端口/协议及网络连通性')
  } finally {
    testing.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.tip { color: #909399; font-size: 12px; margin-left: 8px; }
</style>
