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
        <el-form-item label="备注">
          <el-input v-model="form.remark" placeholder="选填" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  softwarePolicyList, softwarePolicyStatus, softwarePolicyCreate, softwarePolicyUpdate, softwarePolicyDelete,
  updatePortalConfig
} from '../api/auth'

const loading = ref(false)
const savingCfg = ref(false)
const saving = ref(false)
const list = ref([])
const status = reactive({ enabled: false, enforced: false, mode: 'ANY', required: [] })
const enabled = ref(false)
const mode = ref('ANY')
const dlg = ref(false)
const form = reactive({ id: null, name: '', processName: '', sha256: '', matchMode: 'PROCESS', required: 1, enabled: 1, remark: '' })

onMounted(load)

async function load() {
  loading.value = true
  try {
    const [l, s] = await Promise.all([softwarePolicyList(), softwarePolicyStatus()])
    list.value = l.data || []
    Object.assign(status, s.data || {})
    enabled.value = !!status.enabled
    mode.value = status.mode || 'ANY'
  } finally { loading.value = false }
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
  Object.assign(form, { id: null, name: '', processName: '', sha256: '', matchMode: 'PROCESS', required: 1, enabled: 1, remark: '' })
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
