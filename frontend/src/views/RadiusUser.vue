<template>
  <div>
    <el-card>
      <template #header><span>802.1X 口令管理</span></template>

      <!-- 查询 -->
      <el-form :inline="true" @submit.prevent="onQuery">
        <el-form-item label="用户名">
          <el-input v-model="queryName" placeholder="请输入用户名" clearable style="width:240px" @keyup.enter="onQuery" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onQuery" :loading="querying">查询状态</el-button>
        </el-form-item>
      </el-form>

      <!-- 结果 -->
      <el-card v-if="status" shadow="never" class="result">
        <el-descriptions :column="isMobile ? 1 : 3" border>
          <el-descriptions-item label="用户名">{{ status.username }}</el-descriptions-item>
          <el-descriptions-item label="账号状态">
            <el-tag :type="status.status === 1 ? 'success' : 'info'">{{ status.status === 1 ? '启用' : '禁用' }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="802.1X 口令">
            <el-tag :type="status.enabled ? 'success' : 'warning'">{{ status.enabled ? '已开通' : '未开通' }}</el-tag>
          </el-descriptions-item>
        </el-descriptions>
        <div class="ops">
          <el-button type="primary" @click="openSet">{{ status.enabled ? '重置口令' : '开通口令' }}</el-button>
          <el-button type="danger" :disabled="!status.enabled" @click="onClear">清空口令</el-button>
        </div>
      </el-card>
      <el-empty v-else-if="queried" description="未查询或用户不存在" />
    </el-card>

    <!-- 设置口令弹窗 -->
    <el-dialog v-model="setVisible" :title="status && status.enabled ? '重置 802.1X 口令' : '开通 802.1X 口令'" :width="isMobile ? '92%' : '420px'">
      <el-form label-width="90px">
        <el-form-item label="用户名">
          <el-input :model-value="status && status.username" disabled />
        </el-form-item>
        <el-form-item label="口令">
          <el-input v-model="form.password" type="password" show-password placeholder="6-64 位" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="setVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getRadiusStatus, setRadiusPassword, clearRadiusPassword } from '../api/auth'
import { useBreakpoints } from '../composables/useBreakpoints'

const { isMobile } = useBreakpoints()

const queryName = ref('')
const querying = ref(false)
const queried = ref(false)
const status = ref(null)

const setVisible = ref(false)
const saving = ref(false)
const form = ref({ password: '' })

async function onQuery() {
  if (!queryName.value.trim()) { ElMessage.warning('请输入用户名'); return }
  querying.value = true
  try {
    const r = await getRadiusStatus(queryName.value.trim())
    status.value = r.data
    queried.value = true
  } catch (e) {
    status.value = null
    queried.value = true
  } finally {
    querying.value = false
  }
}

function openSet() {
  form.value.password = ''
  setVisible.value = true
}

async function onSave() {
  const pwd = form.value.password
  if (!pwd || pwd.length < 6 || pwd.length > 64) { ElMessage.warning('口令长度需 6-64 位'); return }
  saving.value = true
  try {
    await setRadiusPassword({ username: status.value.username, radiusPassword: pwd })
    ElMessage.success('保存成功')
    setVisible.value = false
    onQuery() // 刷新状态
  } finally {
    saving.value = false
  }
}

async function onClear() {
  try {
    await ElMessageBox.confirm(`确认清空 ${status.value.username} 的 802.1X 口令？清空后该账号将无法准入。`, '提示', { type: 'warning' })
  } catch (e) { return }
  await clearRadiusPassword(status.value.username)
  ElMessage.success('已清空')
  onQuery()
}
</script>

<style scoped>
.result { margin-top: 12px; }
.ops { margin-top: 16px; }
</style>
