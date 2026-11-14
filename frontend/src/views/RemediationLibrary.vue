<template>
  <div>
    <el-card>
      <el-tabs v-model="tab">
        <el-tab-pane label="补丁库" name="patch">
          <div style="margin-bottom:10px;text-align:right">
            <el-button type="primary" size="small" @click="openPatch()">新增补丁</el-button>
          </div>
          <el-table :data="patches" border stripe size="small" v-loading="loading">
            <el-table-column prop="name" label="补丁名称" min-width="160" show-overflow-tooltip />
            <el-table-column prop="version" label="版本" width="110" />
            <el-table-column prop="severity" label="级别" width="90" />
            <el-table-column prop="fileUrl" label="下载地址" min-width="220" show-overflow-tooltip />
            <el-table-column prop="silentArgs" label="静默参数" width="120" />
            <el-table-column label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="row.enabled === 1 ? 'success' : 'info'" size="small">{{ row.enabled === 1 ? '启用' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="130">
              <template #default="{ row }">
                <el-button size="small" type="primary" link @click="openPatch(row)">编辑</el-button>
                <el-button size="small" type="danger" link @click="delPatch(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="病毒库" name="av">
          <div style="margin-bottom:10px;text-align:right">
            <el-button type="primary" size="small" @click="openAv()">新增病毒库</el-button>
          </div>
          <el-table :data="avs" border stripe size="small" v-loading="loading">
            <el-table-column prop="name" label="名称" min-width="150" show-overflow-tooltip />
            <el-table-column prop="vendor" label="厂商" width="120" />
            <el-table-column prop="version" label="病毒库版本" width="130" />
            <el-table-column prop="fileUrl" label="下载地址" min-width="220" show-overflow-tooltip />
            <el-table-column label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="row.enabled === 1 ? 'success' : 'info'" size="small">{{ row.enabled === 1 ? '启用' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="130">
              <template #default="{ row }">
                <el-button size="small" type="primary" link @click="openAv(row)">编辑</el-button>
                <el-button size="small" type="danger" link @click="delAv(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="修复规则" name="rule">
          <div style="margin-bottom:10px;text-align:right">
            <el-button type="primary" size="small" @click="openRule()">新增规则</el-button>
          </div>
          <el-alert type="info" :closable="false" show-icon style="margin-bottom:10px"
            title="终端进入修复区时，按启用规则生成修复任务：缺失项命中 trigger_value（空=任意）即下发对应补丁/病毒库升级包或修复指引。" />
          <el-table :data="rules" border stripe size="small" v-loading="loading">
            <el-table-column prop="name" label="规则名称" min-width="150" show-overflow-tooltip />
            <el-table-column prop="triggerType" label="触发类型" width="150" />
            <el-table-column prop="triggerValue" label="触发值" min-width="140" show-overflow-tooltip />
            <el-table-column prop="actionType" label="修复动作" width="140" />
            <el-table-column label="关联对象" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">{{ targetLabel(row) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="row.enabled === 1 ? 'success' : 'info'" size="small">{{ row.enabled === 1 ? '启用' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="130">
              <template #default="{ row }">
                <el-button size="small" type="primary" link @click="openRule(row)">编辑</el-button>
                <el-button size="small" type="danger" link @click="delRule(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 补丁 -->
    <el-dialog v-model="patchDlg" :title="patchForm.id ? '编辑补丁' : '新增补丁'" width="540px">
      <el-form label-width="100px">
        <el-form-item label="名称"><el-input v-model="patchForm.name" placeholder="如 KB5034441" /></el-form-item>
        <el-form-item label="版本"><el-input v-model="patchForm.version" /></el-form-item>
        <el-form-item label="级别">
          <el-select v-model="patchForm.severity" style="width:160px">
            <el-option v-for="s in ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW']" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item label="下载地址"><el-input v-model="patchForm.fileUrl" /></el-form-item>
        <el-form-item label="安装命令"><el-input v-model="patchForm.installCmd" placeholder="如 wusa.exe /quiet" /></el-form-item>
        <el-form-item label="静默参数"><el-input v-model="patchForm.silentArgs" /></el-form-item>
        <el-form-item label="SHA256"><el-input v-model="patchForm.sha256" placeholder="选填，64 位十六进制" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="patchForm.description" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="patchForm.enabled" :active-value="1" :inactive-value="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="patchDlg = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="savePatch">保存</el-button>
      </template>
    </el-dialog>

    <!-- 病毒库 -->
    <el-dialog v-model="avDlg" :title="avForm.id ? '编辑病毒库' : '新增病毒库'" width="540px">
      <el-form label-width="100px">
        <el-form-item label="名称"><el-input v-model="avForm.name" placeholder="如 360 病毒库" /></el-form-item>
        <el-form-item label="厂商"><el-input v-model="avForm.vendor" /></el-form-item>
        <el-form-item label="版本"><el-input v-model="avForm.version" /></el-form-item>
        <el-form-item label="下载地址"><el-input v-model="avForm.fileUrl" /></el-form-item>
        <el-form-item label="安装命令"><el-input v-model="avForm.installCmd" /></el-form-item>
        <el-form-item label="静默参数"><el-input v-model="avForm.silentArgs" /></el-form-item>
        <el-form-item label="SHA256"><el-input v-model="avForm.sha256" placeholder="选填，64 位十六进制" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="avForm.description" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="avForm.enabled" :active-value="1" :inactive-value="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="avDlg = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveAv">保存</el-button>
      </template>
    </el-dialog>

    <!-- 修复规则 -->
    <el-dialog v-model="ruleDlg" :title="ruleForm.id ? '编辑修复规则' : '新增修复规则'" width="540px">
      <el-form label-width="100px">
        <el-form-item label="规则名称"><el-input v-model="ruleForm.name" /></el-form-item>
        <el-form-item label="触发类型">
          <el-select v-model="ruleForm.triggerType" style="width:200px">
            <el-option label="软件缺失" value="SOFTWARE_MISSING" />
            <el-option label="病毒库过期" value="AV_OUTDATED" />
            <el-option label="补丁缺失" value="PATCH_MISSING" />
          </el-select>
        </el-form-item>
        <el-form-item label="触发值">
          <el-input v-model="ruleForm.triggerValue" placeholder="缺失项包含该值才命中；留空=任意" />
        </el-form-item>
        <el-form-item label="修复动作">
          <el-select v-model="ruleForm.actionType" style="width:200px" @change="ruleForm.targetId = null">
            <el-option label="安装补丁" value="INSTALL_PATCH" />
            <el-option label="更新病毒库" value="UPDATE_AV" />
            <el-option label="打开修复链接" value="OPEN_URL" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="ruleForm.actionType === 'INSTALL_PATCH'" label="关联补丁">
          <el-select v-model="ruleForm.targetId" style="width:100%" filterable>
            <el-option v-for="p in patches" :key="p.id" :label="`${p.name} ${p.version || ''}`" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="ruleForm.actionType === 'UPDATE_AV'" label="关联病毒库">
          <el-select v-model="ruleForm.targetId" style="width:100%" filterable>
            <el-option v-for="a in avs" :key="a.id" :label="`${a.name} ${a.version || ''}`" :value="a.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="ruleForm.actionType === 'OPEN_URL'" label="修复链接">
          <el-input v-model="ruleForm.actionUrl" />
        </el-form-item>
        <el-form-item label="启用"><el-switch v-model="ruleForm.enabled" :active-value="1" :inactive-value="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="ruleDlg = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveRule">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  patchList, patchCreate, patchUpdate, patchDelete,
  avList, avCreate, avUpdate, avDelete,
  remediationRuleList, remediationRuleCreate, remediationRuleUpdate, remediationRuleDelete
} from '../api/auth'

const tab = ref('patch')
const loading = ref(false)
const saving = ref(false)
const patches = ref([])
const avs = ref([])
const rules = ref([])

const patchDlg = ref(false)
const patchForm = reactive({ id: null, name: '', version: '', osType: 'WINDOWS', severity: 'HIGH', sha256: '', fileUrl: '', installCmd: '', silentArgs: '', description: '', enabled: 1 })
const avDlg = ref(false)
const avForm = reactive({ id: null, name: '', vendor: '', version: '', sha256: '', fileUrl: '', installCmd: '', silentArgs: '', description: '', enabled: 1 })
const ruleDlg = ref(false)
const ruleForm = reactive({ id: null, name: '', triggerType: 'SOFTWARE_MISSING', triggerValue: '', actionType: 'INSTALL_PATCH', targetId: null, actionUrl: '', enabled: 1 })

onMounted(loadAll)

async function loadAll() {
  loading.value = true
  try {
    const [p, a, r] = await Promise.all([patchList(), avList(), remediationRuleList()])
    patches.value = p.data || []
    avs.value = a.data || []
    rules.value = r.data || []
  } finally { loading.value = false }
}

function openPatch(row) {
  Object.assign(patchForm, row || { id: null, name: '', version: '', osType: 'WINDOWS', severity: 'HIGH', sha256: '', fileUrl: '', installCmd: '', silentArgs: '', description: '', enabled: 1 })
  patchDlg.value = true
}
async function savePatch() {
  if (!patchForm.name || !patchForm.name.trim()) return ElMessage.warning('请填写补丁名称')
  saving.value = true
  try {
    if (patchForm.id) await patchUpdate({ ...patchForm })
    else await patchCreate({ ...patchForm })
    ElMessage.success('已保存'); patchDlg.value = false; loadAll()
  } finally { saving.value = false }
}
async function delPatch(row) {
  await ElMessageBox.confirm(`确定删除补丁 ${row.name} 吗？`, '提示', { type: 'warning' })
  await patchDelete(row.id); ElMessage.success('已删除'); loadAll()
}

function openAv(row) {
  Object.assign(avForm, row || { id: null, name: '', vendor: '', version: '', sha256: '', fileUrl: '', installCmd: '', silentArgs: '', description: '', enabled: 1 })
  avDlg.value = true
}
async function saveAv() {
  if (!avForm.name || !avForm.name.trim()) return ElMessage.warning('请填写病毒库名称')
  saving.value = true
  try {
    if (avForm.id) await avUpdate({ ...avForm })
    else await avCreate({ ...avForm })
    ElMessage.success('已保存'); avDlg.value = false; loadAll()
  } finally { saving.value = false }
}
async function delAv(row) {
  await ElMessageBox.confirm(`确定删除病毒库 ${row.name} 吗？`, '提示', { type: 'warning' })
  await avDelete(row.id); ElMessage.success('已删除'); loadAll()
}

function openRule(row) {
  Object.assign(ruleForm, row || { id: null, name: '', triggerType: 'SOFTWARE_MISSING', triggerValue: '', actionType: 'INSTALL_PATCH', targetId: null, actionUrl: '', enabled: 1 })
  ruleDlg.value = true
}
async function saveRule() {
  if (!ruleForm.name || !ruleForm.name.trim()) return ElMessage.warning('请填写规则名称')
  if (ruleForm.actionType !== 'OPEN_URL' && !ruleForm.targetId) return ElMessage.warning('请选择关联的补丁/病毒库')
  if (ruleForm.actionType === 'OPEN_URL' && !ruleForm.actionUrl) return ElMessage.warning('请填写修复链接')
  saving.value = true
  try {
    if (ruleForm.id) await remediationRuleUpdate({ ...ruleForm })
    else await remediationRuleCreate({ ...ruleForm })
    ElMessage.success('已保存'); ruleDlg.value = false; loadAll()
  } finally { saving.value = false }
}
async function delRule(row) {
  await ElMessageBox.confirm(`确定删除规则 ${row.name} 吗？`, '提示', { type: 'warning' })
  await remediationRuleDelete(row.id); ElMessage.success('已删除'); loadAll()
}

function targetLabel(row) {
  if (row.actionType === 'INSTALL_PATCH') {
    const p = patches.value.find(x => x.id === row.targetId)
    return p ? `${p.name} ${p.version || ''}` : '-'
  }
  if (row.actionType === 'UPDATE_AV') {
    const a = avs.value.find(x => x.id === row.targetId)
    return a ? `${a.name} ${a.version || ''}` : '-'
  }
  return row.actionUrl || '-'
}
</script>
