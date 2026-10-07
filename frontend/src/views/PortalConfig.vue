<template>
  <div>
    <el-card>
      <template #header>
        <div class="head"><span>Portal 配置管理</span>
          <el-button type="primary" size="small" @click="onSave" :loading="saving">保存</el-button>
        </div>
      </template>
      <el-table :data="list" border>
        <el-table-column prop="configKey" label="配置项" width="200" />
        <el-table-column label="配置值">
          <template #default="{ row }">
            <el-input v-model="row.configValue" />
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="说明" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { portalAdminConfig, updatePortalConfig } from '../api/auth'

const list = ref([])
const saving = ref(false)
async function load() {
  const r = await portalAdminConfig()
  list.value = r.data || []
}
async function onSave() {
  saving.value = true
  try {
    const kv = {}
    list.value.forEach((i) => { kv[i.configKey] = i.configValue })
    await updatePortalConfig(kv)
    ElMessage.success('保存成功')
    load()
  } finally {
    saving.value = false
  }
}
onMounted(load)
</script>

<style scoped>
.head { display: flex; justify-content: space-between; align-items: center; }
</style>
