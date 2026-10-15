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
      <div class="url-tpl">
        <span class="url-tpl-label">带参模板：</span>
        <code class="url-tpl-code">{{ tplUrl }}</code>
        <el-button size="small" :icon="CopyDocument" @click="copyTpl">复制模板</el-button>
      </div>
      <div class="url-chips">
        <span class="url-chips-label">参数状态 <b :class="{ ok: activeCount === urlFieldState.length }">{{ activeCount }}/{{ urlFieldState.length }} 已启用</b>：</span>
        <el-tag v-for="f in urlFieldState" :key="f.key" size="small"
          :type="f.state === 'ok' ? 'success' : (f.state === 'off' ? 'info' : 'danger')" effect="plain">
          {{ f.label }}<template v-if="f.state === 'ok'"> · {{ f.alias }}</template><template v-else-if="f.state === 'off'"> · 已停用</template><template v-else> · 未配置</template>
        </el-tag>
      </div>
      <p class="url-tip">
        带参模板中 <code>${参数名}</code> 由交换机 / AC 在重定向时替换为真实值；参数名取自「URL 参数配置」页签各字段的<b>首个别名</b>，已停用的字段不出现在模板中。
        可在「URL 参数配置」页签按字段 <b>启用 / 停用</b>：停用后不再采集该项，已填参数名保留可随时恢复。
      </p>
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
      <el-tabs v-model="activeTab" class="cfg-tabs">
        <el-tab-pane label="Portal 配置" name="portal">
          <div class="panel-head">
            <span class="t">Portal 配置</span>
            <el-button type="primary" :icon="Check" @click="save(coreRows)">保存</el-button>
          </div>
          <el-table :data="coreRows" border size="default">
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
          <p class="hint">改完点右上角「保存」即可生效（仅保存本页签配置）。页面样式预览与调整请到「Portal 配置 → 页面定制」。</p>
        </el-tab-pane>
        <el-tab-pane label="URL 参数配置" name="urlParam">
          <div class="panel-head">
            <span class="t">URL 参数配置</span>
            <el-button type="primary" :icon="Check" @click="save(urlRows)">保存</el-button>
          </div>
          <el-table :data="urlRows" border size="default">
            <el-table-column prop="configKey" label="配置项" width="180">
              <template #default="{ row }">
                <span class="k">{{ row.configKey }}</span>
                <span class="kl">{{ labelOf(row.configKey) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="采集" width="110">
              <template #default="{ row }">
                <el-switch v-model="enabled[row.configKey]" active-value="true" inactive-value="false"
                  active-text="启用" inactive-text="停用" inline-prompt />
              </template>
            </el-table-column>
            <el-table-column label="状态" width="160">
              <template #default="{ row }">
                <el-tag v-if="enabled[row.configKey] !== 'true'" size="small" type="info" effect="plain">已停用</el-tag>
                <el-tag v-else-if="firstAlias(row.configKey)" size="small" type="success" effect="plain">
                  已启用 · {{ firstAlias(row.configKey) }}
                </el-tag>
                <el-tag v-else size="small" type="danger" effect="plain">未配置（不采集）</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="配置值" min-width="220">
              <template #default="{ row }"><el-input v-model="row.configValue" /></template>
            </el-table-column>
            <el-table-column label="说明" min-width="160">
              <template #default="{ row }">{{ row.remark }}</template>
            </el-table-column>
          </el-table>
          <p class="hint">
            每个字段可单独 <b>启用 / 停用</b>：停用后不再采集该项（不影响其它字段），但已填的参数名会保留，随时可重新启用。
            参数名需与交换机/AC 侧 <span class="mono">url-parameter</span> 配置一致；多个别名用英文逗号分隔，按顺序取第一个命中的参数。
            例如华为 AC 常用 <span class="mono">wlanuserip / wlanacip / wlanacname</span>。采集到的终端信息会写入「在线终端」与「认证日志」。改完点右上角「保存」。
          </p>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
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
  portalProtocol: '协议版本',
  urlParamUserIp: '终端IP参数名', urlParamUserMac: '终端MAC参数名', urlParamNasIp: 'NAS IP参数名',
  urlParamSsid: 'SSID参数名', urlParamNasName: 'NAS名称参数名'
}
// 仅协议/认证策略相关配置在此页编辑；页面样式与认证类型请到「页面定制」（带实时预览）
// 分两个页签：Portal 配置（协议/认证策略）与 URL 参数配置，分别保存
const CORE_KEYS = ['portalProtocol', 'mabEnabled', 'visitorVlan', 'grantMinutes',
  'phoneAuditEnabled', 'antiForgeryEnabled', 'antiForgerySecret',
  'portalV2Enabled', 'portalV2Port', 'portalV2Secret']
const URL_KEYS = ['urlParamUserIp', 'urlParamUserMac', 'urlParamNasIp', 'urlParamSsid', 'urlParamNasName']
const PROTOCOL_KEYS = [...CORE_KEYS, ...URL_KEYS]
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
const activeTab = ref('portal')
const rows = ref([])
const coreRows = computed(() => rows.value.filter(r => CORE_KEYS.includes(r.configKey)))
const urlRows = computed(() => rows.value.filter(r => URL_KEYS.includes(r.configKey)))
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

// URL 参数字段（顺序即模板中的参数顺序）
const URL_FIELDS = [
  { key: 'urlParamUserIp', label: '终端IP' },
  { key: 'urlParamUserMac', label: '终端MAC' },
  { key: 'urlParamNasIp', label: 'NAS IP' },
  { key: 'urlParamSsid', label: 'SSID' },
  { key: 'urlParamNasName', label: 'NAS名称' }
]
/** 采集开关：每个字段对应一个独立的 <field>Enabled 配置键，停用后保留参数名配置 */
const enableKeyOf = (k) => k + 'Enabled'
const enabled = reactive({})
const isOn = (k) => enabled[k] === 'true'
/** 取该字段的首个别名（AC 实际下发的参数名）；未配置返回 '' */
function firstAlias(k) {
  return String(val(k) || '').split(',')[0].trim()
}
/** 带参模板：把「已启用且已配置」字段的首别名拼成 AC 重定向链接（值用 ${参数名} 占位） */
const tplUrl = computed(() => {
  const base = (portalOrigin.value || window.location.origin).replace(/\/+$/, '')
  const qs = URL_FIELDS
    .map(f => {
      if (!isOn(f.key)) return ''
      const a = firstAlias(f.key)
      return a ? a + '=${' + a + '}' : ''
    })
    .filter(Boolean)
    .join('&')
  return base + '/nac/portal' + (qs ? '?' + qs : '')
})
/** 参数状态三态：off=已停用；ok=已启用且有别名；miss=已启用但未配置 */
const urlFieldState = computed(() => URL_FIELDS.map(f => {
  const on = isOn(f.key)
  const alias = firstAlias(f.key)
  return { ...f, alias, state: !on ? 'off' : (alias ? 'ok' : 'miss') }
}))
const activeCount = computed(() => urlFieldState.value.filter(f => f.state === 'ok').length)
async function copyTpl() {
  try { await navigator.clipboard.writeText(tplUrl.value); ElMessage.success('已复制带参模板') }
  catch { ElMessage.error('复制失败，请手动复制') }
}

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

// 收集待保存的键值：URL 字段额外带上其采集开关键
function collect(list) {
  const m = {}
  list.forEach(r => {
    m[r.configKey] = String(r.configValue)
    if (URL_KEYS.includes(r.configKey)) m[enableKeyOf(r.configKey)] = enabled[r.configKey]
  })
  return m
}
// 加载时的值快照，用于只提交真正改动过的项（减少请求体与 WAF 扫描开销）
let snapshot = {}
// 分别保存：仅提交当前页签中「有改动」的配置项，避免互相覆盖并加快保存
async function save(list) {
  const want = collect(list)
  const kv = {}
  Object.keys(want).forEach(k => {
    if (String(want[k]) !== String(snapshot[k] ?? '')) kv[k] = want[k]
  })
  if (!Object.keys(kv).length) {
    ElMessage.info('没有需要保存的修改')
    return
  }
  loading.value = true
  try {
    await updatePortalConfig(kv)
    Object.assign(snapshot, kv)
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
    // 采集开关：无该配置键时默认启用（兼容未迁移的旧库）
    URL_KEYS.forEach(k => { enabled[k] = rawMap[enableKeyOf(k)] === 'false' ? 'false' : 'true' })
    // 只展示协议/认证策略项；页面定制项（站点名/标题/背景/认证类型等）不在此重复编辑
    rows.value = all.filter(x => PROTOCOL_KEYS.includes(x.configKey))
    // 快照需含开关键，才能对「仅切换开关」的改动正确识别
    snapshot = Object.fromEntries(rows.value.map(r => [r.configKey, String(r.configValue ?? '')]))
    URL_KEYS.forEach(k => { snapshot[enableKeyOf(k)] = enabled[k] })
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
.mono { font-family: monospace; color: #606266; }
.url-banner { background: linear-gradient(135deg,#ecf5ff,#f0f9eb); border: 1px solid #d9ecff; border-radius: 8px; padding: 14px 16px; margin-bottom: 12px; }
.url-title { display: flex; align-items: center; gap: 6px; font-weight: 600; color: #303133; margin-bottom: 10px; }
.url-row { display: flex; align-items: center; gap: 8px; }
.url-prefix { color: #606266; font-size: 13px; }
.url-suffix { color: #909399; font-family: monospace; font-size: 13px; }
.url-full { margin-top: 8px; font-size: 13px; color: #606266; }
.url-full a { color: #409eff; font-family: monospace; }
.url-tpl { margin-top: 8px; display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.url-tpl-label { font-size: 13px; color: #606266; }
.url-tpl-code { font-family: monospace; font-size: 12px; color: #303133; background: #fff; border: 1px solid #e4e7ed; border-radius: 4px; padding: 3px 8px; word-break: break-all; flex: 1; min-width: 320px; }
.url-chips { margin-top: 8px; display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.url-chips-label { font-size: 13px; color: #606266; }
.url-chips-label b { color: #f56c6c; }
.url-chips-label b.ok { color: #67c23a; }
.url-tip { margin: 8px 0 0; font-size: 12px; color: #909399; line-height: 1.6; }
.url-tip code { font-family: monospace; color: #606266; background: #fff; border: 1px solid #e4e7ed; border-radius: 3px; padding: 0 4px; }
.url-tip b { color: #e6a23c; }
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
