<template>
  <el-card>
    <template #header><span>操作日志</span></template>
    <el-form :inline="true" style="margin-bottom:12px">
      <el-form-item label="操作">
        <el-input v-model="query.operation" placeholder="如：新增账号、修改NAS共享密钥" clearable style="width:240px" @keyup.enter="onSearch" />
      </el-form-item>
      <el-form-item label="时间">
        <el-date-picker v-model="query.range" type="datetimerange" range-separator="至"
          start-placeholder="开始时间" end-placeholder="结束时间" value-format="YYYY-MM-DD HH:mm:ss" style="width:360px" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="onSearch">查询</el-button>
        <el-button @click="onReset">重置</el-button>
      </el-form-item>
    </el-form>
    <el-table :data="list" border v-loading="loading">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="operator" label="操作人" width="120" />
      <el-table-column prop="role" label="角色" width="100" />
      <el-table-column prop="operation" label="操作" width="180" />
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
import { reactive, ref, onMounted } from 'vue'
import { logList } from '../api/auth'
import { fmtTime } from '../utils/format'
const fmt = fmtTime

const list = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const loading = ref(false)
const query = reactive({ operation: '', range: [] })

async function load() {
  loading.value = true
  try {
    const params = { page: page.value, size: size.value }
    if (query.operation.trim()) params.operation = query.operation.trim()
    if (query.range && query.range.length === 2) {
      params.startTime = query.range[0]
      params.endTime = query.range[1]
    }
    const r = await logList(params)
    list.value = r.data.list
    total.value = r.data.total
  } finally {
    loading.value = false
  }
}
function onSearch() { page.value = 1; load() }
function onReset() { query.operation = ''; query.range = []; page.value = 1; load() }
function onPage(p) { page.value = p; load() }
onMounted(load)
</script>
