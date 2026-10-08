<template>
  <el-card>
    <template #header><span>操作日志</span></template>
    <el-table :data="list" border v-loading="loading">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="operator" label="操作人" width="120" />
      <el-table-column prop="role" label="角色" width="100" />
      <el-table-column prop="operation" label="操作" width="160" />
      <el-table-column prop="ip" label="IP" width="140" />
      <el-table-column prop="status" label="结果" width="100" />
      <el-table-column prop="costMs" label="耗时(ms)" width="100" />
      <el-table-column label="时间" width="180">
        <template #default="{ row }">{{ fmt(row.createTime) }}</template>
      </el-table-column>
    </el-table>
    <el-pagination style="margin-top:12px" background layout="prev, pager, next, total"
      :total="total" :page-size="size" :current-page="page" @current-change="onPage" />
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { logList } from '../api/auth'
import { fmtTime } from '../utils/format'
const fmt = fmtTime

const list = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const loading = ref(false)
async function load() {
  loading.value = true
  try {
    const r = await logList({ page: page.value, size: size.value })
    list.value = r.data.list
    total.value = r.data.total
  } finally {
    loading.value = false
  }
}
function onPage(p) { page.value = p; load() }
onMounted(load)
</script>
