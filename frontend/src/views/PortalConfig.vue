<template>
  <div class="pconfig" v-loading="loading">
    <!-- Portal 访问地址：WiFi 用户入网入口，可修改、可复制、可打开 -->
    <div class="url-banner">
      <div class="url-title">
        <el-icon><Link /></el-icon>
        <span>Portal 访问地址（WiFi 用户打开此链接进行认证）</span>
      </div>
      <div class="url-row">
        <span class="url-prefix">主机</span>
        <el-input v-model="portalOrigin" placeholder="https://192.168.100.2" clearable style="flex:1"
          @change="saveOrigin" />
        <span class="url-suffix">/nac/portal</span>
        <el-button :icon="CopyDocument" @click="copyUrl">复制链接</el-button>
        <el-button type="primary" :icon="Open" @click="openUrl">打开</el-button>
      </div>
      <div class="url-full">
        完整地址：<a :href="fullUrl" target="_blank" rel="noopener">{{ fullUrl }}</a>
      </div>
    </div>

    <!-- Portal 协议（硬件准入联动）运行时状态：仅指交换机/AC 的 Portal v1/v2 UDP 协议监听，与 Web 认证无关 -->
    <div class="proto-banner">
      <div class="proto-title">
        <el-icon><Cpu /></el-icon>
        <span>Portal 协议（硬件联动）状态</span>
        <el-tag v-if="rtStatus.running" type="success" effect="dark" round>运行中</el-tag>
        <el-tag v-else-if="rtStatus.enabled" type="warning" effect="dark" round>启动异常</el-tag>
        <el-tag v-else type="info" effect="plain" round>未启用</el-tag>
      </div>
      <div class="proto-row">
        <span class="proto-label">当前协议版本</span>
        <span class="proto-ver" :class="{ on: rtStatus.running }">{{ rtStatus.running ? (rtStatus.activeVersion || '读取中…') : (rtStatus.enabled ? '监听未就绪' : '默认关闭') }}</span>
        <span class="proto-divider">|</span>
        <span class="proto-label">监听端口</span>
        <span>UDP {{ rtStatus.port ?? '-' }}</span>
        <span class="proto-divider">|</span>
        <span class="proto-label">配置策略</span>
        <span>{{ protoStrategyText }}</span>
      </div>
      <p class="proto-note">
        此项仅表示交换机 / AC 等设备的 Portal 协议联动端口是否开启，默认关闭属正常。
        <b>终端 Web 认证（短信 / 账号）由本系统直接提供，与该状态无关，始终可用。</b>
      </p>
    </div>

    <div class="panel">
      <div class="panel-head">
        <span class="t">Portal 配置</span>
        <el-button type="primary" :icon="Check" @click="save">保存</el-button>
      </div>
      <el-table :data="rows" border size="default">
        <el-table-column prop="configKey" label="配置项" width="180">
          <template #default="{ row }">
            <span class="k">{{ row.configKey }}</span>
            <span class="kl">{{ labelOf(row.configKey) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="configValue" label="配置值">
          <template #default="{ row }">
            <el-switch v-if="isBool(row.configKey)"
              v-model="row.configValue" active-value="true" inactive-value="false" />
            <el-input v-else-if="isSecret(row.configKey)" v-model="row.configValue"
              type="password" show-password placeholder="请输入共享密码" />
            <el-input-number v-else-if="isNumber(row.configKey)" v-model="row.configValue"
              :min="0" :max="65535" controls-position="right" style="width:160px" />
            <el-select v-else-if="isSelect(row.configKey)" v-model="row.configValue" style="width:200px">
              <el-option v-for="o in SELECTS[row.configKey]" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
            <el-input v-else v-model="row.configValue" />
          </template>
        </el-table-column>
        <el-table-column label="说明" min-width="160">
          <template #default="{ row }">{{ row.remark }}</template>
        </el-table-column>
      </el-table>
      <p class="hint">改完点右上角「保存」即可生效。页面样式预览与调整请到「Portal 配置 → 页面定制」。</p>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { Check, Link, CopyDocument, Open, Cpu } from '@element-plus/icons-vue'
import { portalAdminConfig, updatePortalConfig, portalStatus } from '../api/auth'

// 运行时协议状态（后端 radius 服务实际监听情况）
const rtStatus = ref({ running: false, enabled: false, port: null, activeVersion: '', protocol: 'both' })
const protoStrategyText = computed(() => {
  const p = rtStatus.value.protocol
  if (p === 'v1') return '仅 v1'
  if (p === 'v2') return '仅 v2'
  return '兼容 v1/v2'
})
let statusTimer = null
async function loadStatus() {
  try {
    const r = await portalStatus()
    if (r && r.data) rtStatus.value = r.data
  } catch (e) { /* 状态读取失败不阻塞页面 */ }
}

const LABELS = {
  mabEnabled: 'MAB 自动认证',
  visitorVlan: '访客 VLAN', grantMinutes: '授权时长(分钟)', phoneAuditEnabled: '手机号审核',
  antiForgeryEnabled: '防伪推开关', antiForgerySecret: '防伪推共享密码',
  portalV2Enabled: 'Portal v2.0 协议', portalV2Port: 'Portal v2.0 端口', portalV2Secret: 'Portal v2.0 共享密钥',
  portalProtocol: '协议版本'
}
// 仅协议/认证策略相关配置在此页编辑；页面样式与认证类型请到「页面定制」（带实时预览）
const PROTOCOL_KEYS = ['portalProtocol', 'mabEnabled', 'visitorVlan', 'grantMinutes',
  'phoneAuditEnabled', 'antiForgeryEnabled', 'antiForgerySecret',
  'portalV2Enabled', 'portalV2Port', 'portalV2Secret']
const BOOLS = ['mabEnabled', 'phoneAuditEnabled', 'antiForgeryEnabled', 'portalV2Enabled']
const SECRETS = ['antiForgerySecret', 'portalV2Secret']
const NUMBERS = ['portalV2Port', 'grantMinutes', 'visitorVlan']
const SELECTS = {
  portalProtocol: [
    { value: 'both', label: '兼容 v1 / v2（推荐）' },
    { value: 'v1', label: '仅 Portal 1.0' },
    { value: 'v2', label: '仅 Portal 2.0' }
  ]
}

const loading = ref(false)
const rows = ref([])
const map = computed(() => Object.fromEntries(rows.value.map(r => [r.configKey, r.configValue])))
const val = (k) => map.value[k] ?? ''
const labelOf = (k) => LABELS[k] || ''
const isBool = (k) => BOOLS.includes(k)
const isSecret = (k) => SECRETS.includes(k)
const isNumber = (k) => NUMBERS.includes(k)
const isSelect = (k) => !!SELECTS[k]

// Portal 访问地址：主机可改，完整地址 = 主机 + /nac/portal
const portalOrigin = ref('')
const fullUrl = computed(() => {
  const base = (portalOrigin.value || window.location.origin).replace(/\/+$/, '')
  return base + '/nac/portal'
})
async function saveOrigin() {
  try {
    await updatePortalConfig({ portalUrl: portalOrigin.value })
    ElMessage.success('访问地址已保存')
  } catch (e) { ElMessage.error('保存失败') }
}
async function copyUrl() {
  try { await navigator.clipboard.writeText(fullUrl.value); ElMessage.success('已复制：' + fullUrl.value) }
  catch { ElMessage.error('复制失败，请手动复制') }
}
function openUrl() { window.open(fullUrl.value, '_blank', 'noopener') }

async function save() {
  loading.value = true
  try {
    const kv = {}
    rows.value.forEach(r => (kv[r.configKey] = String(r.configValue)))
    await updatePortalConfig(kv)
    ElMessage.success('已保存')
    // 配置热生效（后端 10s 对账），稍后刷新运行状态
    setTimeout(loadStatus, 12000)
  } finally { loading.value = false }
}
onMounted(async () => {
  loading.value = true
  try {
    const r = await portalAdminConfig()
    const all = r.data || []
    const rawMap = Object.fromEntries(all.map(x => [x.configKey, x.configValue]))
    portalOrigin.value = rawMap.portalUrl || ''
    // 只展示协议/认证策略项；页面定制项（站点名/标题/背景/认证类型等）不在此重复编辑
    rows.value = all.filter(x => PROTOCOL_KEYS.includes(x.configKey))
  } finally { loading.value = false }
  loadStatus()
  statusTimer = setInterval(loadStatus, 15000)
})
onBeforeUnmount(() => { if (statusTimer) clearInterval(statusTimer) })
</script>

<style scoped>
.pconfig .panel { background: #fff; border-radius: 8px; padding: 16px; margin-bottom: 12px; }
.panel-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.panel-head .t { font-weight: 600; font-size: 15px; }
.k { font-family: monospace; font-size: 13px; }
.kl { color: #909399; font-size: 12px; margin-left: 6px; }
.hint { color: #909399; font-size: 12px; margin: 10px 2px 0; }
.url-banner { background: linear-gradient(135deg,#ecf5ff,#f0f9eb); border: 1px solid #d9ecff; border-radius: 8px; padding: 14px 16px; margin-bottom: 12px; }
.url-title { display: flex; align-items: center; gap: 6px; font-weight: 600; color: #303133; margin-bottom: 10px; }
.url-row { display: flex; align-items: center; gap: 8px; }
.url-prefix { color: #606266; font-size: 13px; }
.url-suffix { color: #909399; font-family: monospace; font-size: 13px; }
.url-full { margin-top: 8px; font-size: 13px; color: #606266; }
.url-full a { color: #409eff; font-family: monospace; }
.proto-banner { background: linear-gradient(135deg,#f0f9eb,#ecf5ff); border: 1px solid #e1f3d8; border-radius: 8px; padding: 12px 16px; margin-bottom: 12px; }
.proto-title { display: flex; align-items: center; gap: 8px; font-weight: 600; color: #303133; margin-bottom: 8px; }
.proto-row { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; font-size: 13px; color: #606266; }
.proto-label { color: #909399; }
.proto-ver { font-weight: 600; font-size: 14px; color: #909399; }
.proto-ver.on { color: #67c23a; }
.proto-divider { color: #dcdfe6; }
.proto-note { margin: 8px 0 0; font-size: 12px; color: #909399; line-height: 1.6; }
.proto-note b { color: #67c23a; }
</style>
