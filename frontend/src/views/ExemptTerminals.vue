<template>
  <div>
    <el-card v-loading="loading">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span>免认证终端</span>
          <el-button type="primary" size="small" @click="openCreate">新增终端</el-button>
        </div>
      </template>
      <el-alert type="info" :closable="false" show-icon style="margin-bottom:12px"
        title="绑定 MAC 或 IP 的终端命中白名单后，Portal 与 802.1X(RADIUS) 认证直接放行，无需账号口令。MAC、IP 至少填一个。" />

      <el-table :data="list" border stripe>
        <el-table-column prop="mac" label="MAC 地址" min-width="170">
          <template #default="{ row }">{{ row.mac || '-' }}</template>
        </el-table-column>
        <el-table-column prop="ip" label="IP 地址" min-width="140">
          <template #default="{ row }">{{ row.ip || '-' }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remark || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 1 ? 'success' : 'info'" size="small">{{ row.enabled === 1 ? '启用' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="160">
        <template #default="{ row }">{{ fmt(row.createTime) }}</template>
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

    <el-dialog v-model="dlg" :title="form.id ? '编辑终端' : '新增终端'" width="460px" @close="saving = false">
      <el-form label-width="90px">
        <el-form-item label="MAC 地址"><el-input v-model="form.mac" placeholder="如 aa:bb:cc:dd:ee:ff（可留空）" /></el-form-item>
        <el-form-item label="IP 地址"><el-input v-model="form.ip" placeholder="如 192.168.10.88（可留空）" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" placeholder="如 打印机 / 会议室终端" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" /></el-form-item>
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
import { exemptList, exemptCreate, exemptUpdate, exemptDelete } from '../api/auth'
import { fmtTime } from '../utils/format'
const fmt = fmtTime

const loading = ref(false)
const list = ref([])
const dlg = ref(false)
const saving = ref(false)
const form = reactive({ id: null, mac: '', ip: '', remark: '', enabled: 1 })

onMounted(load)
async function load() {
  loading.value = true
  try { list.value = (await exemptList()).data || [] } finally { loading.value = false }
}
function resetForm() { Object.assign(form, { id: null, mac: '', ip: '', remark: '', enabled: 1 }) }
function openCreate() { resetForm(); dlg.value = true }
function openEdit(row) { Object.assign(form, row); dlg.value = true }
async function onSave() {
  saving.value = true
  try {
    if (form.id) await exemptUpdate({ ...form })
    else await exemptCreate({ ...form })
    ElMessage.success('保存成功'); dlg.value = false; load()
  } finally { saving.value = false }
}
async function toggle(row) {
  await exemptUpdate({ ...row, enabled: row.enabled === 1 ? 0 : 1 })
  ElMessage.success('已更新'); load()
}
async function onDelete(row) {
  await ElMessageBox.confirm(`确定删除 ${row.mac || row.ip} 吗？`, '提示', { type: 'warning' })
  await exemptDelete(row.id)
  ElMessage.success('已删除'); load()
}
</script>
