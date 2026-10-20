<template>
  <div>
    <el-card v-loading="loading">
      <template #header><span>短信网关配置</span></template>
      <el-alert type="info" :closable="false" show-icon
        title="选择当前启用的短信网关并填写对应云厂商凭证。保存后立即生效，Portal 短信登录将走该网关。未填写的字段回退服务端默认配置。"
        style="margin-bottom: 16px" />

      <el-form label-width="120px" style="max-width: 780px">
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

      <!-- 阿里云：两类短信服务 Tab 切换 -->
      <div v-if="cfg.provider === 'aliyun'" style="max-width: 920px">
        <el-tabs v-model="cfg.aliyun.mode" class="sms-aliyun-tabs">
          <el-tab-pane label="短信认证服务" name="auth">
            <el-alert type="success" :closable="false" show-icon
              title="无需申请资质、签名与模板。阿里云短信认证服务使用「系统赠送」的签名与模板并托管验证码的生成、下发与核验，仅需 AccessKey 凭证即可开通。"
              style="margin-bottom: 12px" />
            <el-form label-width="150px" style="max-width: 680px">
              <el-form-item label="AccessKey ID"><el-input v-model="cfg.aliyun.accessKeyId" /></el-form-item>
              <el-form-item label="AccessKey Secret"><el-input v-model="cfg.aliyun.accessKeySecret" type="password" show-password /></el-form-item>
              <el-form-item label="认证方案名称">
                <el-input v-model="cfg.aliyun.schemeName" placeholder="可选，阿里云控制台「认证方案」名称，如 NACLogin" />
              </el-form-item>
              <el-form-item label="签名名称（赠送）">
                <el-input v-model="cfg.aliyun.authSignName" placeholder="留空则用系统赠送签名：恒创联众" />
              </el-form-item>
              <el-form-item label="模板 Code（赠送）">
                <el-input v-model="cfg.aliyun.authTemplateCode" placeholder="留空则用系统赠送模板：100001" />
              </el-form-item>
              <el-form-item label="验证码位数">
                <el-input-number v-model="cfg.aliyun.codeLength" :min="4" :max="8" />
                <span class="hint">默认 6 位</span>
              </el-form-item>
              <el-form-item label="有效期（秒）">
                <el-input-number v-model="cfg.aliyun.validTime" :min="60" :max="3600" :step="60" />
                <span class="hint">默认 300 秒</span>
              </el-form-item>
            </el-form>
          </el-tab-pane>

          <el-tab-pane label="短信验证码" name="code">
            <el-alert type="warning" :closable="false" show-icon
              title="需先在阿里云控制台申请短信签名与模板（须企业资质、签名实名制报备）。验证码由本系统生成并在本地校验。"
              style="margin-bottom: 12px" />
            <el-form label-width="150px" style="max-width: 680px">
              <el-form-item label="AccessKey ID"><el-input v-model="cfg.aliyun.accessKeyId" /></el-form-item>
              <el-form-item label="AccessKey Secret"><el-input v-model="cfg.aliyun.accessKeySecret" type="password" show-password /></el-form-item>
              <el-form-item label="短信签名"><el-input v-model="cfg.aliyun.signName" placeholder="自申请签名，如：NAC准入" /></el-form-item>
              <el-form-item label="模板 Code"><el-input v-model="cfg.aliyun.templateCode" placeholder="自申请模板，如：SMS_123456789" /></el-form-item>
            </el-form>
          </el-tab-pane>
        </el-tabs>

        <el-divider content-position="left">两类短信服务区别</el-divider>
        <el-table :data="aliyunDiff" border size="small" style="width: 100%">
          <el-table-column prop="item" label="对比项" width="160" />
          <el-table-column prop="auth" label="短信认证服务" min-width="220" />
          <el-table-column prop="code" label="短信验证码" min-width="260" />
        </el-table>
        <div class="hint" style="margin-top: 8px">
          注：短信认证服务无需申请资质/签名/模板，验证码生命周期与防刷由阿里云托管；短信验证码需自行申请签名模板并实现验证码生成与校验。
        </div>
      </div>

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
  aliyun: {
    mode: 'code',
    accessKeyId: '',
    accessKeySecret: '',
    signName: '',
    templateCode: '',
    authSignName: '',
    authTemplateCode: '',
    schemeName: '',
    codeLength: 6,
    validTime: 300
  },
  tencent: { secretId: '', secretKey: '', region: '', appId: '', signName: '', templateId: '' },
  huawei: { appKey: '', appSecret: '', sender: '', templateId: '', url: '' },
  custom: { url: '', method: 'POST', contentType: 'json', headers: '', bodyTemplate: '', successContains: '' }
})

// 阿里云两类短信服务区别（与官方文档表格一致）
const aliyunDiff = [
  { item: '申请资质', auth: '不需要', code: '必须提供企业资质，不支持个人资质' },
  { item: '申请签名和模板', auth: '不需要，短信认证服务提供', code: '必须' },
  { item: '签名实名制报备', auth: '不需要', code: '必须' },
  { item: '验证码生命周期管理', auth: '短信认证服务提供验证码全生命周期管理，包括验证码的生成规则、核验策略和回填率统计', code: '开发者实现' },
  { item: '验证码功能安全', auth: '短信认证服务提供验证码防刷能力，以及验证码接口的安全稳定性保障', code: '开发者实现主要的防盗刷能力，短信服务提供防盗刷预警配置和接口的安全稳定性保障' },
  { item: '接入方式', auth: 'API、SDK、HTTP协议', code: 'API、SDK、HTTP协议、控制台' },
  { item: '收费方式', auth: '发送失败不计费', code: '发送失败不计费' }
]

onMounted(load)
async function load() {
  loading.value = true
  try {
    const r = await getSmsConfig()
    const d = r.data || {}
    cfg.provider = d.provider || 'mock'
    Object.assign(cfg.aliyun, d.aliyun || {})
    if (!cfg.aliyun.mode) cfg.aliyun.mode = 'code'
    if (cfg.aliyun.codeLength == null) cfg.aliyun.codeLength = 6
    if (cfg.aliyun.validTime == null) cfg.aliyun.validTime = 300
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

<style scoped>
.sms-aliyun-tabs :deep(.el-tabs__header) { margin-bottom: 16px; }
.hint { margin-left: 8px; color: #909399; font-size: 12px; }
</style>
