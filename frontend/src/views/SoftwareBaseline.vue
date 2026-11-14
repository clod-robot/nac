<template>
  <div>
    <el-card v-loading="loading" style="margin-bottom:12px">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span>终端软件识别</span>
          <div>
            <el-switch v-model="enabled" :active-value="true" :inactive-value="false"
              active-text="启用" inactive-text="关闭" :loading="savingCfg" @change="saveCfg" style="margin-right:16px" />
            <el-radio-group v-model="mode" :disabled="!enabled" @change="saveCfg">
              <el-radio-button label="ANY">满足任一</el-radio-button>
              <el-radio-button label="ALL">全部满足</el-radio-button>
            </el-radio-group>
          </div>
        </div>
      </template>
      <el-alert :type="status.enforced ? 'success' : 'warning'" :closable="false" show-icon
        :title="status.enforced
          ? `策略已生效：终端必须运行指定软件才能通过认证（${mode === 'ALL' ? '全部必需项' : '任一必需项'}），当前必需软件 ${ (status.required || []).join('、') || '无' }`
          : '策略未生效：需启用开关并至少添加一条“必须满足”的软件项；免认证终端与未开启认证的终端不受影响。'" />
      <el-alert type="info" :closable="false" show-icon style="margin-top:10px"
        title="判定依据：802.1X 客户端实时上报进程名 + 可执行文件 SHA256；进程名匹配且（如配置）哈希一致才算命中，可防御同名替换伪装。终端未安装/未运行认证客户端时无法通过认证。" />
    </el-card>

    <el-card v-loading="rtLoading" style="margin-bottom:12px">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span>在线终端实时合规状态</span>
          <div>
            <span class="tip" style="margin-right:12px">每 30 秒自动刷新 · 最近刷新 {{ lastRefresh || '-' }}</span>
            <el-button size="small" @click="loadRealtime">立即刷新</el-button>
          </div>
        </div>
      </template>
      <el-alert v-if="!rt.enforced" type="info" :closable="false" show-icon
        title="策略未生效：不进行合规判定。启用并配置必需软件后，未运行指定软件的终端将被标记为不合规并断开网络。" />
      <el-table :data="rt.rows" border stripe size="small" style="margin-top:10px">
        <el-table-column prop="username" label="账号" min-width="120" show-overflow-tooltip />
        <el-table-column prop="mac" label="MAC" width="150" />
        <el-table-column prop="ip" label="IP" width="130" />
        <el-table-column prop="hostname" label="主机名" min-width="130" show-overflow-tooltip />
        <el-table-column label="合规状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.compliant ? 'success' : 'danger'" size="small">
              {{ row.compliant ? '合规' : (row.reported ? '不合规' : '未上报') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="缺失软件" min-width="180">
          <template #default="{ row }">
            <template v-if="(row.missing || []).length">
              <el-tag v-for="m in row.missing" :key="m" type="danger" size="small" style="margin-right:4px">{{ m }}</el-tag>
            </template>
            <span v-else class="tip">-</span>
          </template>
        </el-table-column>
        <el-table-column label="客户端" width="110">
          <template #default="{ row }">{{ row.clientVersion || '-' }}</template>
        </el-table-column>
        <el-table-column label="最近上报" width="170">
          <template #default="{ row }">{{ fmtTime(row.lastReport) }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!rt.rows.length" description="暂无在线终端" :image-size="60" />
    </el-card>

    <el-card style="margin-bottom:12px">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span>合规弹窗模板（全局）</span>
          <el-button size="small" type="primary" @click="openPopupDlg">编辑模板</el-button>
        </div>
      </template>
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="标题">{{ popup.title || '-' }}</el-descriptions-item>
        <el-descriptions-item label="按钮">{{ popup.actionText || '-' }}</el-descriptions-item>
        <el-descriptions-item label="正文" :span="2">{{ popup.content || '-' }}</el-descriptions-item>
        <el-descriptions-item label="暂缓">
          {{ popup.allowDelay ? `允许，最长 ${popup.delaySec} 秒` : '不允许（必须立即处理）' }}
        </el-descriptions-item>
        <el-descriptions-item label="按钮地址">{{ popup.actionUrl || '-' }}</el-descriptions-item>
      </el-descriptions>
      <div class="tip" style="margin-top:8px">终端校验未通过时弹窗提示；策略中填写的弹窗字段优先于全局模板，正文支持 {software} 占位符。</div>
    </el-card>

    <el-card v-loading="loading">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span>软件策略</span>
          <el-button type="primary" size="small" @click="openCreate">新增软件</el-button>
        </div>
      </template>

      <el-table :data="list" border stripe>
        <el-table-column prop="name" label="软件名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="processName" label="进程名" min-width="140">
          <template #default="{ row }">{{ row.processName }}.exe</template>
        </el-table-column>
        <el-table-column label="校验方式" width="120">
          <template #default="{ row }">
            <el-tag :type="row.matchMode === 'HASH' ? 'success' : 'warning'" size="small">
              {{ row.matchMode === 'HASH' ? '进程名+哈希' : '仅进程名' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sha256" label="SHA256" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span style="font-family:monospace;font-size:12px">{{ row.sha256 || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="必需" width="90">
          <template #default="{ row }">
            <el-tag :type="row.required === 1 ? 'danger' : 'info'" size="small">{{ row.required === 1 ? '必须' : '记录' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="校验模式" width="130">
          <template #default="{ row }">
            <el-tag :type="row.checkMode === 'ONCE' ? 'warning' : 'success'" size="small">
              {{ row.checkMode === 'ONCE' ? '仅认证时校验' : '实时校验' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remark || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 1 ? 'success' : 'info'" size="small">{{ row.enabled === 1 ? '启用' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="openEdit(row)">编辑</el-button>
            <el-button size="small" :type="row.enabled === 1 ? 'warning' : 'success'" link @click="toggle(row)">
              {{ row.enabled === 1 ? '禁用' : '启用' }}
            </el-button>
            <el-button size="small" type="danger" link @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dlg" :title="form.id ? '编辑软件策略' : '新增软件策略'" width="520px">
      <el-form label-width="100px">
        <el-form-item label="软件名称">
          <el-input v-model="form.name" placeholder="如 企业微信" />
        </el-form-item>
        <el-form-item label="进程名">
          <el-input v-model="form.processName" placeholder="如 WeChatWork（无需路径，可带 .exe）" />
        </el-form-item>
        <el-form-item label="校验方式">
          <el-radio-group v-model="form.matchMode" @change="onModeChange">
            <el-radio label="PROCESS">仅进程名</el-radio>
            <el-radio label="HASH">进程名 + 文件哈希</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.matchMode === 'HASH'" label="SHA256">
          <el-input v-model="form.sha256" placeholder="64 位小写十六进制" />
          <div class="tip">在终端执行 certutil -hashfile 路径.exe SHA256 可获取；上传文件可在「参数配置」核对。</div>
        </el-form-item>
        <el-form-item label="必需">
          <el-switch v-model="form.required" :active-value="1" :inactive-value="0" />
          <span class="tip" style="margin-left:8px">必须满足则参与认证阻断；否则仅记录</span>
        </el-form-item>
        <el-form-item label="校验模式">
          <el-radio-group v-model="form.checkMode">
            <el-radio label="REALTIME">实时校验</el-radio>
            <el-radio label="ONCE">认证时校验，通过后不再校验</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" placeholder="选填" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-divider content-position="left">策略级弹窗（留空使用全局模板）</el-divider>
        <el-form-item label="弹窗标题">
          <el-input v-model="form.popupTitle" placeholder="留空使用全局模板" maxlength="128" />
        </el-form-item>
        <el-form-item label="弹窗正文">
          <el-input v-model="form.popupContent" type="textarea" :rows="3" maxlength="2000"
            placeholder="支持 {software} 占位符，留空使用全局模板" />
        </el-form-item>
        <el-form-item label="按钮文案">
          <el-input v-model="form.popupActionText" placeholder="留空使用全局模板" maxlength="64" />
        </el-form-item>
        <el-form-item label="按钮地址">
          <el-input v-model="form.popupActionUrl" placeholder="如 https://下载/修复地址，留空使用全局模板" maxlength="512" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="popupDlg" title="编辑全局合规弹窗" width="560px">
      <el-form label-width="110px">
        <el-form-item label="标题">
          <el-input v-model="popupForm.title" maxlength="128" />
        </el-form-item>
        <el-form-item label="正文">
          <el-input v-model="popupForm.content" type="textarea" :rows="4" maxlength="2000" />
          <div class="tip">支持 {software} 占位符，将替换为缺失软件名。</div>
        </el-form-item>
        <el-form-item label="按钮文案">
          <el-input v-model="popupForm.actionText" maxlength="64" />
        </el-form-item>
        <el-form-item label="按钮地址">
          <el-input v-model="popupForm.actionUrl" maxlength="512" placeholder="点击按钮打开的下载/修复地址" />
        </el-form-item>
        <el-form-item label="允许暂缓">
          <el-switch v-model="popupForm.allowDelay" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item v-if="popupForm.allowDelay === 1" label="暂缓秒数">
          <el-input-number v-model="popupForm.delaySec" :min="0" :max="3600" />
          <span class="tip" style="margin-left:8px">0 表示可无限暂缓</span>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="popupForm.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="popupDlg = false">取消</el-button>
        <el-button type="primary" :loading="popupSaving" @click="savePopup">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  softwarePolicyList, softwarePolicyStatus, softwarePolicyCreate, softwarePolicyUpdate, softwarePolicyDelete,
  softwarePolicyRealtime, updatePortalConfig, getPopupTemplate, savePopupTemplate
} from '../api/auth'

const loading = ref(false)
const savingCfg = ref(false)
const saving = ref(false)
const list = ref([])
const status = reactive({ enabled: false, enforced: false, mode: 'ANY', required: [] })
const enabled = ref(false)
const mode = ref('ANY')
const dlg = ref(false)
const form = reactive({
  id: null, name: '', processName: '', sha256: '', matchMode: 'PROCESS', required: 1, enabled: 1,
  checkMode: 'REALTIME', popupTitle: '', popupContent: '', popupActionText: '', popupActionUrl: '', remark: ''
})

// 全局弹窗模板
const popupDlg = ref(false)
const popupSaving = ref(false)
const popup = reactive({ title: '', content: '', actionText: '', actionUrl: '', delaySec: 30, allowDelay: 1, enabled: 1 })
const popupForm = reactive({ title: '', content: '', actionText: '', actionUrl: '', delaySec: 30, allowDelay: 1, enabled: 1 })

// 在线终端实时合规状态
const rtLoading = ref(false)
const rt = reactive({ enforced: false, mode: 'ANY', required: [], rows: [] })
const lastRefresh = ref('')
let rtTimer = null

onMounted(() => {
  load()
  loadRealtime()
  rtTimer = setInterval(loadRealtime, 30000)
})
onUnmounted(() => { if (rtTimer) clearInterval(rtTimer) })

function fmtTime(sec) {
  if (!sec) return '-'
  const d = new Date(Number(sec) * 1000)
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

async function loadRealtime() {
  rtLoading.value = true
  try {
    const r = await softwarePolicyRealtime()
    const d = r.data || {}
    rt.enforced = !!d.enforced
    rt.mode = d.mode || 'ANY'
    rt.required = d.required || []
    rt.rows = d.rows || []
    lastRefresh.value = fmtTime(Math.floor(Date.now() / 1000))
  } finally { rtLoading.value = false }
}

async function load() {
  loading.value = true
  try {
    const [l, s, p] = await Promise.all([softwarePolicyList(), softwarePolicyStatus(), getPopupTemplate()])
    list.value = l.data || []
    Object.assign(status, s.data || {})
    Object.assign(popup, p.data || {})
    enabled.value = !!status.enabled
    mode.value = status.mode || 'ANY'
  } finally { loading.value = false }
}

function openPopupDlg() {
  Object.assign(popupForm, popup)
  popupDlg.value = true
}

async function savePopup() {
  if (!popupForm.title || !popupForm.title.trim()) return ElMessage.warning('请填写弹窗标题')
  if (!popupForm.content || !popupForm.content.trim()) return ElMessage.warning('请填写弹窗正文')
  popupSaving.value = true
  try {
    await savePopupTemplate({ ...popupForm, id: 1 })
    Object.assign(popup, popupForm)
    ElMessage.success('弹窗模板已保存')
    popupDlg.value = false
  } finally { popupSaving.value = false }
}

async function saveCfg() {
  savingCfg.value = true
  try {
    await updatePortalConfig({ softwareBaselineEnabled: String(enabled.value), softwareBaselineMode: mode.value })
    ElMessage.success(enabled.value ? '已启用终端软件识别' : '已关闭终端软件识别')
    await load()
  } finally { savingCfg.value = false }
}

function resetForm() {
  Object.assign(form, {
    id: null, name: '', processName: '', sha256: '', matchMode: 'PROCESS', required: 1, enabled: 1,
    checkMode: 'REALTIME', popupTitle: '', popupContent: '', popupActionText: '', popupActionUrl: '', remark: ''
  })
}
function openCreate() { resetForm(); dlg.value = true }
function openEdit(row) { Object.assign(form, row); dlg.value = true }
function onModeChange() { if (form.matchMode !== 'HASH') form.sha256 = '' }

async function onSave() {
  if (!form.processName || !form.processName.trim()) return ElMessage.warning('请填写进程名')
  if (form.matchMode === 'HASH' && !/^[0-9a-fA-F]{64}$/.test((form.sha256 || '').trim()))
    return ElMessage.warning('哈希校验需填写 64 位 SHA256')
  saving.value = true
  try {
    if (form.id) await softwarePolicyUpdate({ ...form })
    else await softwarePolicyCreate({ ...form })
    ElMessage.success('保存成功'); dlg.value = false; load()
  } finally { saving.value = false }
}

async function toggle(row) {
  await softwarePolicyUpdate({ ...row, enabled: row.enabled === 1 ? 0 : 1 })
  ElMessage.success('已更新'); load()
}

async function onDelete(row) {
  await ElMessageBox.confirm(`确定删除 ${row.name || row.processName} 吗？`, '提示', { type: 'warning' })
  await softwarePolicyDelete(row.id)
  ElMessage.success('已删除'); load()
}
</script>

<style scoped>
.tip { font-size: 12px; color: #909399; line-height: 1.6; }
</style>
