<template>
  <el-card>
    <template #header>
      <div class="head">
        <span>账号管理</span>
        <div class="head-right">
          <el-input v-model="keyword" placeholder="搜索账号/姓名/手机号" clearable size="small"
            style="width:210px;margin-right:8px" @keyup.enter="onSearch" @clear="onSearch">
            <template #append><el-button @click="onSearch">搜索</el-button></template>
          </el-input>
          <el-select v-model="deptFilter" placeholder="按部门筛选" clearable size="small" style="width:160px;margin-right:8px">
            <el-option v-for="d in deptOptions" :key="d" :label="d" :value="d" />
          </el-select>
          <el-button type="primary" size="small" @click="openCreate">新增账号</el-button>
          <el-button size="small" @click="downloadTemplate">下载模板</el-button>
          <el-button size="small" type="success" :loading="importing" @click="fileInput?.click()">批量导入</el-button>
          <input ref="fileInput" type="file" accept=".csv" style="display:none" @change="onImportFile" />
        </div>
      </div>
    </template>

    <el-table :data="filteredList" border v-loading="loading">
      <el-table-column prop="username" label="账号" min-width="120" />
      <el-table-column prop="realName" label="归属人" min-width="110" />
      <el-table-column label="联系电话" min-width="130">
        <template #default="{ row }">{{ row.phoneMasked || '-' }}</template>
      </el-table-column>
      <el-table-column label="部门" min-width="140">
        <template #default="{ row }">
          <el-select v-model="row.dept" placeholder="选择/输入部门" size="small" filterable allow-create default-first-option
            style="width:100%" @change="saveDept(row)">
            <el-option v-for="d in deptOptions" :key="d" :label="d" :value="d" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="角色" width="100">
        <template #default="{ row }">
          <el-tag :type="row.roleCode === 'admin' ? 'danger' : 'info'">{{ row.roleCode === 'admin' ? '管理员' : '普通用户' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="认证方式" width="120">
        <template #default="{ row }">
          <el-select v-model="row.authMethod" size="small" style="width:100%" @change="saveAuthMethod(row)">
            <el-option label="Portal 认证" value="portal" />
            <el-option label="EAP-TLS 认证" value="eap-tls" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="终端数量" width="150">
        <template #default="{ row }">
          <el-input-number v-model="row.terminalLimit" :min="0" :max="9999" size="small" controls-position="right" @change="saveLimit(row)" />
        </template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'warning'">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" min-width="160">
        <template #default="{ row }">{{ fmt(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="270" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button v-if="row.roleCode !== 'admin'" size="small" @click="toggleStatus(row)">{{ row.status === 1 ? '禁用' : '启用' }}</el-button>
          <el-button size="small" type="warning" @click="openReset(row)">重置密码</el-button>
          <el-button v-if="row.roleCode !== 'admin'" size="small" type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination style="margin-top:12px" background layout="prev, pager, next, total"
      :total="total" :page-size="size" :current-page="page" @current-change="onPage" />

    <!-- 新增账号 -->
    <el-dialog v-model="createVisible" title="新增账号" :width="isMobile ? '92%' : '420px'">
      <el-form label-width="80px">
        <el-form-item label="账号"><el-input v-model="form.username" placeholder="登录账号" /></el-form-item>
        <el-form-item label="密码"><el-input v-model="form.password" type="password" show-password placeholder="留空则默认 admin123，首次登录强制修改" /></el-form-item>
        <el-form-item label="归属人"><el-input v-model="form.realName" placeholder="姓名/归属人" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="form.phone" placeholder="手机号/联系电话" maxlength="15" /></el-form-item>
        <el-form-item label="部门">
          <el-select v-model="form.dept" placeholder="选择/输入部门" filterable allow-create default-first-option style="width:100%">
            <el-option v-for="d in deptOptions" :key="d" :label="d" :value="d" />
          </el-select>
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.roleCode" style="width:100%">
            <el-option label="普通用户" value="user" />
            <el-option label="管理员" value="admin" />
          </el-select>
        </el-form-item>
        <el-form-item label="认证方式">
          <el-select v-model="form.authMethod" style="width:100%">
            <el-option label="Portal 认证" value="portal" />
            <el-option label="EAP-TLS 认证" value="eap-tls" />
          </el-select>
        </el-form-item>
        <el-form-item label="终端数量"><el-input-number v-model="form.terminalLimit" :min="0" :max="9999" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onCreate">确定</el-button>
      </template>
    </el-dialog>

    <!-- 编辑资料：归属人/部门/联系电话（含管理员） -->
    <el-dialog v-model="editVisible" title="编辑资料" :width="isMobile ? '92%' : '420px'">
      <el-form label-width="90px">
        <el-form-item label="账号"><el-input :model-value="editTarget?.username" disabled /></el-form-item>
        <el-form-item label="归属人"><el-input v-model="editForm.realName" placeholder="姓名/归属人" /></el-form-item>
        <el-form-item label="部门">
          <el-select v-model="editForm.dept" placeholder="选择/输入部门" filterable allow-create default-first-option style="width:100%">
            <el-option v-for="d in deptOptions" :key="d" :label="d" :value="d" />
          </el-select>
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="editForm.phone" placeholder="留空则不修改；填写则更新（用于短信登录/找回）" maxlength="15" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onEdit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 重置密码 -->
    <el-dialog v-model="resetVisible" title="重置密码" :width="isMobile ? '92%' : '380px'">
      <el-descriptions :column="1" border size="small" style="margin-bottom:12px">
        <el-descriptions-item label="账号">{{ resetTarget?.username || '-' }}</el-descriptions-item>
        <el-descriptions-item label="归属部门">{{ resetTarget?.dept || '-' }}</el-descriptions-item>
        <el-descriptions-item label="使用人(姓名)">{{ resetTarget?.realName || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-input v-model="newPwd" type="password" show-password placeholder="新密码（至少 6 位）" />
      <template #footer>
        <el-button @click="resetVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onReset">确定</el-button>
      </template>
    </el-dialog>

    <!-- 批量导入结果 -->
    <el-dialog v-model="importResultVisible" title="批量导入结果" :width="isMobile ? '92%' : '440px'">
      <el-alert type="success" :closable="false" show-icon style="margin-bottom:10px"
        :title="`共 ${importResult.total} 行，成功 ${importResult.success}，失败 ${importResult.fail}`" />
      <div v-if="importResult.errors && importResult.errors.length" style="max-height:260px;overflow:auto">
        <div v-for="e in importResult.errors" :key="e.row" style="font-size:12px;color:#f56c6c;line-height:1.8">
          第 {{ e.row }} 行（{{ e.username || '空' }}）：{{ e.message }}
        </div>
      </div>
      <template #footer><el-button type="primary" @click="importResultVisible = false">知道了</el-button></template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { userList, userCreate, userUpdateStatus, userUpdateLimit, userUpdateDept, userUpdateAuthMethod, userUpdateProfile, userResetPassword, userDelete, userDownloadTemplate, userImport } from '../api/auth'
import { fmtTime } from '../utils/format'
const fmt = fmtTime
import { useBreakpoints } from '../composables/useBreakpoints'

const { isMobile } = useBreakpoints()
const list = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const loading = ref(false)
const deptFilter = ref('')
const keyword = ref('')

const deptOptions = computed(() => [...new Set(list.value.map(u => u.dept).filter(Boolean))])
const filteredList = computed(() => deptFilter.value ? list.value.filter(u => u.dept === deptFilter.value) : list.value)

async function load() {
  loading.value = true
  try {
    const r = await userList({ page: page.value, size: size.value, keyword: keyword.value || undefined })
    list.value = r.data.list || []
    total.value = r.data.total || 0
  } finally {
    loading.value = false
  }
}
function onPage(p) { page.value = p; load() }
function onSearch() { page.value = 1; load() }

const createVisible = ref(false)
const saving = ref(false)
const form = ref({ username: '', password: '', realName: '', phone: '', dept: '', roleCode: 'user', terminalLimit: 5, authMethod: 'eap-tls' })
function openCreate() {
  form.value = { username: '', password: '', realName: '', phone: '', dept: '', roleCode: 'user', terminalLimit: 5, authMethod: 'eap-tls' }
  createVisible.value = true
}
async function onCreate() {
  saving.value = true
  try {
    await userCreate(form.value)
    ElMessage.success('已创建')
    createVisible.value = false
    load()
  } finally { saving.value = false }
}

const editVisible = ref(false)
let editTarget = null
const editForm = ref({ realName: '', dept: '', phone: '' })
function openEdit(row) {
  editTarget = row
  editForm.value = { realName: row.realName || '', dept: row.dept || '', phone: '' }
  editVisible.value = true
}
async function onEdit() {
  saving.value = true
  try {
    await userUpdateProfile({ id: editTarget.id, realName: editForm.value.realName, dept: editForm.value.dept, phone: editForm.value.phone || undefined })
    ElMessage.success('资料已更新')
    editVisible.value = false
    load()
  } finally { saving.value = false }
}

async function toggleStatus(row) {
  await userUpdateStatus({ id: row.id, status: row.status === 1 ? 0 : 1 })
  ElMessage.success('已更新')
  load()
}
async function saveLimit(row) {
  try {
    await userUpdateLimit({ id: row.id, terminalLimit: row.terminalLimit })
    ElMessage.success('终端数量已更新')
  } catch (e) { load() }
}
async function saveDept(row) {
  try {
    await userUpdateDept({ id: row.id, dept: row.dept || null })
    ElMessage.success('部门已更新')
  } catch (e) { load() }
}
async function saveAuthMethod(row) {
  try {
    await userUpdateAuthMethod({ id: row.id, authMethod: row.authMethod || 'eap-tls' })
    ElMessage.success('认证方式已更新')
  } catch (e) { load() }
}

const resetVisible = ref(false)
const newPwd = ref('')
let resetTarget = null
function openReset(row) { resetTarget = row; newPwd.value = ''; resetVisible.value = true }
async function onReset() {
  if (!newPwd.value || newPwd.value.length < 6) { ElMessage.warning('密码至少 6 位'); return }
  saving.value = true
  try {
    await userResetPassword({ id: resetTarget.id, password: newPwd.value })
    ElMessage.success('密码已重置')
    resetVisible.value = false
  } finally { saving.value = false }
}

async function onDelete(row) {
  try {
    await ElMessageBox.confirm(`确认删除账号「${row.username}」？`, '提示', { type: 'warning' })
  } catch (e) { return }
  await userDelete(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(load)

// ===== 批量导入 =====
const fileInput = ref(null)
const importing = ref(false)
const importResultVisible = ref(false)
const importResult = ref({ total: 0, success: 0, fail: 0, errors: [] })

async function downloadTemplate() {
  try {
    const resp = await userDownloadTemplate()
    const blob = new Blob([resp.data], { type: 'text/csv;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url; a.download = '用户导入模板.csv'; a.click()
    URL.revokeObjectURL(url)
  } catch (e) { ElMessage.error('模板下载失败') }
}
async function onImportFile(e) {
  const f = e.target.files[0]
  e.target.value = ''
  if (!f) return
  importing.value = true
  try {
    const fd = new FormData(); fd.append('file', f)
    const r = await userImport(fd)
    importResult.value = r.data || { total: 0, success: 0, fail: 0, errors: [] }
    importResultVisible.value = true
    ElMessage.success(`导入完成：成功 ${importResult.value.success}，失败 ${importResult.value.fail}`)
    load()
  } finally { importing.value = false }
}
</script>

<style scoped>
.head { display: flex; justify-content: space-between; align-items: center; }
.head-right { display: flex; align-items: center; }
</style>
