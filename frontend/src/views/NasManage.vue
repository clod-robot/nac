<template>
  <div>
    <!-- 共享密钥 -->
    <el-card>
      <template #header><span>NAS 共享密钥（接入密钥）</span></template>
      <el-alert
        type="info" :closable="false" show-icon
        title="该密钥是 RADIUS 服务端与 NAS/交换机之间约定的共享密钥。NAS 设备（802.1X 认证）必须配置成与此处一致。修改后立即生效。"
        style="margin-bottom: 14px" />
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="共享密钥">
          <el-input v-model="secret" type="password" show-password placeholder="未自定义则使用服务端默认密钥" style="width: 320px" maxlength="64" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onSaveSecret" :loading="saving">保存密钥</el-button>
          <el-tag v-if="!customized" type="warning" style="margin-left: 8px">当前使用默认密钥</el-tag>
          <el-tag v-else type="success" style="margin-left: 8px">已自定义</el-tag>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- NAS 设备列表 -->
    <el-card style="margin-top: 14px">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span>已认证的 NAS 设备</span>
          <el-button size="small" :icon="Refresh" @click="loadList" :loading="loading">刷新</el-button>
        </div>
      </template>
      <el-table :data="list" v-loading="loading" empty-text="暂无 NAS 设备发起认证" stripe>
        <el-table-column prop="nasIp" label="NAS IP" width="150" />
        <el-table-column label="名称" min-width="150">
          <template #default="{ row }">
            <span>{{ row.nasName || ('NAS-' + row.nasIp) }}</span>
            <el-button link type="primary" size="small" style="margin-left:8px" @click="openRename(row)">改名</el-button>
          </template>
        </el-table-column>
        <el-table-column prop="nasIdentifier" label="NAS-Identifier" min-width="140" show-overflow-tooltip />
        <el-table-column prop="lastUser" label="最近认证账号" width="140" />
        <el-table-column prop="successCount" label="成功" width="90" align="center">
          <template #default="{ row }"><el-tag type="success" size="small">{{ row.successCount || 0 }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="failCount" label="失败" width="90" align="center">
          <template #default="{ row }"><el-tag type="danger" size="small">{{ row.failCount || 0 }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="lastSeen" label="最后认证时间" min-width="170" />
      </el-table>
    </el-card>

    <!-- 改名弹窗 -->
    <el-dialog v-model="renameVisible" title="修改 NAS 名称" :width="isMobile ? '92%' : '400px'">
      <el-form label-width="80px">
        <el-form-item label="NAS IP"><el-input :model-value="cur && cur.nasIp" disabled /></el-form-item>
        <el-form-item label="名称">
          <el-input v-model="nameInput" maxlength="64" show-word-limit placeholder="如：核心交换机-3F" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="renameVisible = false">取消</el-button>
        <el-button type="primary" :loading="renaming" @click="onRename">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getNasSecret, setNasSecret, listNas, renameNas } from '../api/auth'
import { useBreakpoints } from '../composables/useBreakpoints'

const { isMobile } = useBreakpoints()

const secret = ref('')
const customized = ref(false)
const saving = ref(false)

const list = ref([])
const loading = ref(false)

const renameVisible = ref(false)
const cur = ref(null)
const nameInput = ref('')
const renaming = ref(false)

async function loadSecret() {
  try {
    const r = await getNasSecret()
    secret.value = r.data.secret || ''
    customized.value = !!r.data.customized
  } catch (e) { /* ignore */ }
}
async function onSaveSecret() {
  const s = secret.value.trim()
  if (!s) { ElMessage.warning('密钥不能为空'); return }
  if (s.length < 4 || s.length > 64) { ElMessage.warning('密钥长度需 4-64 位'); return }
  saving.value = true
  try {
    await setNasSecret({ secret: s })
    ElMessage.success('密钥已更新，立即生效')
    loadSecret()
  } finally { saving.value = false }
}

async function loadList() {
  loading.value = true
  try {
    const r = await listNas()
    list.value = r.data || []
  } finally { loading.value = false }
}

function openRename(row) {
  cur.value = row
  nameInput.value = row.nasName || ''
  renameVisible.value = true
}
async function onRename() {
  renaming.value = true
  try {
    await renameNas({ nasIp: cur.value.nasIp, nasName: nameInput.value.trim() })
    ElMessage.success('已修改')
    renameVisible.value = false
    loadList()
  } finally { renaming.value = false }
}

onMounted(() => { loadSecret(); loadList() })
</script>
