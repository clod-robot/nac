<template>
  <div>
    <el-card v-loading="loading">
      <template #header><span>短信网关配置</span></template>
      <el-alert type="info" :closable="false" show-icon
        title="选择当前启用的短信网关并填写对应云厂商凭证。保存后立即生效，Portal 短信登录将走该网关。未填写的字段回退服务端默认配置。"
        style="margin-bottom: 16px" />

      <el-form label-width="120px" style="max-width: 640px">
        <el-form-item label="启用网关">
          <el-radio-group v-model="cfg.provider">
            <el-radio value="mock">模拟（不真实发送）</el-radio>
            <el-radio value="aliyun">阿里云</el-radio>
            <el-radio value="tencent">腾讯云</el-radio>
            <el-radio value="huawei">华为云</el-radio>
            <el-radio value="custom">自定义网关</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>

      <!-- 阿里云 -->
      <el-form v-if="cfg.provider === 'aliyun'" label-width="140px" style="max-width: 640px">
        <el-form-item label="AccessKey ID"><el-input v-model="cfg.aliyun.accessKeyId" /></el-form-item>
        <el-form-item label="AccessKey Secret"><el-input v-model="cfg.aliyun.accessKeySecret" type="password" show-password /></el-form-item>
        <el-form-item label="短信签名"><el-input v-model="cfg.aliyun.signName" placeholder="如：NAC准入" /></el-form-item>
        <el-form-item label="模板 Code"><el-input v-model="cfg.aliyun.templateCode" placeholder="如：SMS_123456" /></el-form-item>
      </el-form>

      <!-- 腾讯云 -->
      <el-form v-else-if="cfg.provider === 'tencent'" label-width="140px" style="max-width: 640px">
        <el-form-item label="SecretId"><el-input v-model="cfg.tencent.secretId" /></el-form-item>
        <el-form-item label="SecretKey"><el-input v-model="cfg.tencent.secretKey" type="password" show-password /></el-form-item>
        <el-form-item label="Region"><el-input v-model="cfg.tencent.region" placeholder="ap-guangzhou" /></el-form-item>
        <el-form-item label="SmsSdkAppId"><el-input v-model="cfg.tencent.appId" /></el-form-item>
        <el-form-item label="短信签名"><el-input v-model="cfg.tencent.signName" /></el-form-item>
        <el-form-item label="模板 ID"><el-input v-model="cfg.tencent.templateId" /></el-form-item>
      </el-form>

      <!-- 华为云 -->
      <el-form v-else-if="cfg.provider === 'huawei'" label-width="140px" style="max-width: 640px">
        <el-form-item label="APP Key"><el-input v-model="cfg.huawei.appKey" /></el-form-item>
        <el-form-item label="APP Secret"><el-input v-model="cfg.huawei.appSecret" type="password" show-password /></el-form-item>
        <el-form-item label="发送方 sender"><el-input v-model="cfg.huawei.sender" /></el-form-item>
        <el-form-item label="模板 ID"><el-input v-model="cfg.huawei.templateId" /></el-form-item>
        <el-form-item label="请求 URL"><el-input v-model="cfg.huawei.url" /></el-form-item>
      </el-form>

      <!-- 自定义网关 -->
      <el-form v-else-if="cfg.provider === 'custom'" label-width="120px" style="max-width: 720px">
        <el-form-item label="请求 URL">
          <el-input v-model="cfg.custom.url" placeholder="如 https://sms.example.com/send?phone={phone}&code={code}" />
        </el-form-item>
        <el-form-item label="请求方法">
          <el-radio-group v-model="cfg.custom.method">
            <el-radio value="POST">POST</el-radio>
            <el-radio value="GET">GET</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="报文类型">
          <el-radio-group v-model="cfg.custom.contentType">
            <el-radio value="json">JSON</el-radio>
            <el-radio value="form">表单</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="请求头">
          <el-input v-model="cfg.custom.headers" type="textarea" :rows="2"
            placeholder="每行一个，格式 Key: Value，例如：&#10;Authorization: Bearer xxx&#10;X-Api-Key: abc" />
        </el-form-item>
        <el-form-item label="报文模板">
          <el-input v-model="cfg.custom.bodyTemplate" type="textarea" :rows="4"
            placeholder="支持占位符 {phone} {code} {signName} {template}，例如：&#10;{&quot;mobile&quot;:&quot;{phone}&quot;,&quot;content&quot;:&quot;您的验证码是{code}&quot;}" />
        </el-form-item>
        <el-form-item label="成功判定">
          <el-input v-model="cfg.custom.successContains"
            placeholder="响应体需包含的子串（留空则以 HTTP 2xx 为成功），如：&quot;code&quot;:0" />
        </el-form-item>
      </el-form>

      <el-form v-else label-width="140px" style="max-width: 640px">
        <el-form-item label="说明">当前为模拟网关，验证码不会真实下发，仅用于联调。</el-form-item>
      </el-form>

      <el-button type="primary" :loading="saving" @click="onSave">保存配置</el-button>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getSmsConfig, setSmsConfig } from '../api/auth'

const loading = ref(true)
const saving = ref(false)
const cfg = reactive({
  provider: 'mock',
  aliyun: { accessKeyId: '', accessKeySecret: '', signName: '', templateCode: '' },
  tencent: { secretId: '', secretKey: '', region: '', appId: '', signName: '', templateId: '' },
  huawei: { appKey: '', appSecret: '', sender: '', templateId: '', url: '' },
  custom: { url: '', method: 'POST', contentType: 'json', headers: '', bodyTemplate: '', successContains: '' }
})

onMounted(load)
async function load() {
  loading.value = true
  try {
    const r = await getSmsConfig()
    const d = r.data || {}
    cfg.provider = d.provider || 'mock'
    Object.assign(cfg.aliyun, d.aliyun || {})
    Object.assign(cfg.tencent, d.tencent || {})
    Object.assign(cfg.huawei, d.huawei || {})
    Object.assign(cfg.custom, d.custom || {})
  } finally {
    loading.value = false
  }
}
async function onSave() {
  saving.value = true
  try {
    await setSmsConfig(JSON.parse(JSON.stringify(cfg)))
    ElMessage.success('保存成功，已即时生效')
    load()
  } finally {
    saving.value = false
  }
}
</script>
