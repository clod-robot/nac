<template>
  <div>
    <el-row :gutter="12" style="margin-bottom:12px">
      <el-col :span="8">
        <el-card><el-statistic title="隔离中终端" :value="stat.active || 0" /></el-card>
      </el-col>
      <el-col :span="8">
        <el-card><el-statistic title="隔离区" :value="stat.quarantine || 0" /></el-card>
      </el-col>
      <el-col :span="8">
        <el-card><el-statistic title="修复区" :value="stat.remediation || 0" /></el-card>
      </el-col>
    </el-row>

    <el-card v-loading="cfgLoading" style="margin-bottom:12px">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span>隔离下发配置</span>
          <el-button size="small" type="primary" :loading="cfgSaving" @click="saveCfg">保存配置</el-button>
        </div>
      </template>
      <el-form :model="cfg" label-width="150px" size="small">
        <el-form-item label="启用隔离区">
          <el-switch v-model="cfg.enabled" :active-value="1" :inactive-value="0" />
          <span class="tip" style="margin-left:8px">启用后校验未通过不再断网，而是下发隔离区 ACL / VLAN</span>
        </el-form-item>
        <el-form-item label="下发通道">
          <el-radio-group v-model="cfg.mode">
            <el-radio-button label="ACL">ACL 隔离</el-radio-button>
            <el-radio-button label="VLAN">VLAN 隔离</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <template v-if="cfg.mode === 'ACL'">
          <el-form-item label="隔离区 ACL 名">
            <el-input v-model="cfg.quarantineAcl" placeholder="Filter-Id，如 NAC-QUARANTINE" />
          </el-form-item>
          <el-form-item label="修复区 ACL 名">
            <el-input v-model="cfg.remediationAcl" placeholder="Filter-Id，如 NAC-REMEDIATION" />
          </el-form-item>
        </template>
        <template v-else>
          <el-form-item label="隔离区 VLAN">
            <el-input-number v-model="cfg.quarantineVlan" :min="1" :max="4094" />
          </el-form-item>
          <el-form-item label="修复区 VLAN">
            <el-input-number v-model="cfg.remediationVlan" :min="1" :max="4094" />
          </el-form-item>
        </template>
        <el-form-item label="修复门户地址">
          <el-input v-model="cfg.remediationPortalUrl" placeholder="终端进入修复区后打开的地址（可空）" />
        </el-form-item>
        <el-form-item label="放通服务器 IP">
          <el-input v-model="cfg.remediationServerIps" placeholder="补丁/病毒库服务器 IP，逗号分隔" />
        </el-form-item>
        <el-form-item label="合规后自动释放">
          <el-switch v-model="cfg.autoRelease" :active-value="1" :inactive-value="0" />
          <span class="tip" style="margin-left:8px">关闭后需管理员在下方记录中人工释放</span>
        </el-form-item>
        <el-form-item label="最短隔离时长">
          <el-input-number v-model="cfg.releaseHoldMinutes" :min="0" :max="1440" />
          <span class="tip" style="margin-left:8px">分钟，合规后需满足该时长才自动释放，0=立即</span>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card v-loading="loading">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span>隔离记录</span>
          <div>
            <el-select v-model="statusFilter" size="small" style="width:130px;margin-right:8px" @change="loadRecords">
              <el-option label="全部" value="" />
              <el-option label="隔离中" value="ACTIVE" />
              <el-option label="已释放" value="RELEASED" />
            </el-select>
            <el-button size="small" @click="loadRecords">刷新</el-button>
          </div>
        </div>
      </template>
      <el-table :data="records" border stripe size="small">
        <el-table-column prop="mac" label="MAC" width="150" />
        <el-table-column prop="ip" label="IP" width="130" />
        <el-table-column prop="username" label="账号" min-width="110" show-overflow-tooltip />
        <el-table-column label="区域" width="100">
          <template #default="{ row }">
            <el-tag :type="row.zone === 'REMEDIATION' ? 'warning' : 'danger'" size="small">
              {{ row.zone === 'REMEDIATION' ? '修复区' : '隔离区' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'danger' : 'info'" size="small">
              {{ row.status === 'ACTIVE' ? '隔离中' : '已释放' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="reason" label="原因" min-width="180" show-overflow-tooltip />
        <el-table-column label="下发通道" width="150">
          <template #default="{ row }">{{ row.channelType ? `${row.channelType}: ${row.channelValue}` : '-' }}</template>
        </el-table-column>
        <el-table-column label="进入时间" width="170">
          <template #default="{ row }">{{ fmt(row.enterTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="showTasks(row)">修复任务</el-button>
            <el-button v-if="row.status === 'ACTIVE' && row.zone !== 'REMEDIATION'" size="small" type="warning" link
              @click="toRemediate(row)">转修复区</el-button>
            <el-button v-if="row.status === 'ACTIVE'" size="small" type="danger" link @click="release(row)">释放</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!records.length" description="暂无隔离记录" :image-size="60" />
    </el-card>

    <el-dialog v-model="taskDlg" title="修复任务" width="760px">
      <el-table :data="tasks" border size="small">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="title" label="任务" min-width="220" show-overflow-tooltip />
        <el-table-column prop="libType" label="类型" width="90" />
        <el-table-column label="安装命令" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.installCmd || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="taskTag(row.status)" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!tasks.length" description="暂无任务（请在「补丁与病毒库」配置修复规则）" :image-size="60" />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getIsolationConfig, saveIsolationConfig, isolationRecords, isolationStats,
  isolationRelease, isolationRemediate, remediationTasks
} from '../api/auth'

const cfgLoading = ref(false)
const cfgSaving = ref(false)
const loading = ref(false)
const statusFilter = ref('ACTIVE')
const records = ref([])
const stat = reactive({ active: 0, quarantine: 0, remediation: 0, released: 0 })
const cfg = reactive({
  enabled: 0, mode: 'ACL', quarantineAcl: 'NAC-QUARANTINE', remediationAcl: 'NAC-REMEDIATION',
  quarantineVlan: 300, remediationVlan: 301, remediationPortalUrl: '', remediationServerIps: '',
  autoRelease: 1, releaseHoldMinutes: 30
})
const taskDlg = ref(false)
const tasks = ref([])
let timer = null

onMounted(() => { loadCfg(); loadRecords(); loadStats(); timer = setInterval(loadRecords, 30000) })
onUnmounted(() => { if (timer) clearInterval(timer) })

function fmt(v) {
  if (!v) return '-'
  return String(v).replace('T', ' ').substring(0, 19)
}
function taskTag(s) {
  if (s === 'DONE') return 'success'
  if (s === 'FAILED') return 'danger'
  if (s === 'RUNNING') return 'warning'
  return 'info'
}

async function loadCfg() {
  cfgLoading.value = true
  try {
    const r = await getIsolationConfig()
    Object.assign(cfg, r.data || {})
  } finally { cfgLoading.value = false }
}

async function saveCfg() {
  if (cfg.mode === 'ACL' && (!cfg.quarantineAcl || !cfg.remediationAcl))
    return ElMessage.warning('请填写隔离区/修复区 ACL 名称')
  if (cfg.mode === 'VLAN' && cfg.quarantineVlan === cfg.remediationVlan)
    return ElMessage.warning('隔离区与修复区 VLAN 不能相同')
  cfgSaving.value = true
  try {
    await saveIsolationConfig({ ...cfg, id: 1 })
    ElMessage.success('配置已保存')
    loadRecords(); loadStats()
  } finally { cfgSaving.value = false }
}

async function loadRecords() {
  loading.value = true
  try {
    const r = await isolationRecords({ status: statusFilter.value, limit: 200 })
    records.value = r.data || []
  } finally { loading.value = false }
}

async function loadStats() {
  try {
    const r = await isolationStats()
    Object.assign(stat, r.data || {})
  } catch (e) { /* 忽略统计失败 */ }
}

async function release(row) {
  await ElMessageBox.confirm(`确定释放 ${row.mac} 吗？释放后下次认证恢复正式网络。`, '提示', { type: 'warning' })
  await isolationRelease(row.id, '管理员人工释放')
  ElMessage.success('已释放')
  loadRecords(); loadStats()
}

async function toRemediate(row) {
  await isolationRemediate(row.id)
  ElMessage.success('已转入修复区，并生成修复任务')
  loadRecords(); loadStats(); showTasks(row)
}

async function showTasks(row) {
  const r = await remediationTasks({ recordId: row.id })
  tasks.value = r.data || []
  taskDlg.value = true
}
</script>

<style scoped>
.tip { font-size: 12px; color: #909399; }
</style>
