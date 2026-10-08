<template>
  <div class="authlog" v-loading="loading">
    <div class="bar">
      <span class="t">认证日志</span>
      <div class="filters">
        <el-input v-model="f.username" placeholder="用户" clearable style="width:150px" @keyup.enter="reload" />
        <el-input v-model="f.mac" placeholder="MAC地址" clearable style="width:170px" @keyup.enter="reload" />
        <el-input v-model="f.ip" placeholder="终端IP" clearable style="width:150px" @keyup.enter="reload" />
        <el-select v-model="f.type" placeholder="类型" clearable style="width:130px" @change="reload">
          <el-option label="登录 login" value="login" />
          <el-option label="Portal" value="portal" />
          <el-option label="RADIUS" value="radius" />
        </el-select>
        <el-select v-model="f.result" placeholder="结果" clearable style="width:120px" @change="reload">
          <el-option label="成功" :value="1" />
          <el-option label="失败" :value="0" />
        </el-select>
        <el-button :icon="Search" type="primary" @click="reload">查询</el-button>
        <el-button :icon="Refresh" @click="reset">重置</el-button>
      </div>
    </div>
    <el-table :data="list" border>
      <el-table-column prop="createTime" label="时间" width="170" />
      <el-table-column label="类型" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="typeTag(row.authType)">{{ row.authType }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="账号/手机号" min-width="140">
        <template #default="{ row }">{{ row.username || row.phone || '-' }}</template>
      </el-table-column>
      <el-table-column prop="mac" label="MAC" width="150" />
      <el-table-column label="IP / NAS" min-width="150">
        <template #default="{ row }">
          <div>{{ row.ip || '-' }}</div>
          <div v-if="row.nasIp" class="sub">{{ row.nasIp }}</div>
        </template>
      </el-table-column>
      <el-table-column label="结果" width="80">
        <template #default="{ row }">
          <el-tag size="small" :type="row.result === 1 ? 'success' : 'danger'">
            {{ row.result === 1 ? '成功' : '失败' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="message" label="说明" min-width="160" show-overflow-tooltip />
    </el-table>
    <div class="pg">
      <el-pagination background layout="total, prev, pager, next" :total="total" :current-page="page" :page-size="size" @current-change="onPage" />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Refresh, Search } from '@element-plus/icons-vue'
import { authLogList } from '../api/auth'

const list = ref([]); const total = ref(0); const page = ref(1); const size = ref(20)
const loading = ref(false)
const f = ref({ username: '', mac: '', ip: '', type: '', result: null })
const typeTag = (t) => ({ login: '', portal: 'warning', radius: 'success' }[t] || 'info')

async function load() {
  loading.value = true
  try {
    const params = { page: page.value, size: size.value }
    if (f.value.username) params.username = f.value.username
    if (f.value.mac) params.mac = f.value.mac
    if (f.value.ip) params.ip = f.value.ip
    if (f.value.type) params.type = f.value.type
    if (f.value.result !== null && f.value.result !== '' && f.value.result !== undefined) params.result = f.value.result
    const r = await authLogList(params)
    list.value = r.data.list; total.value = r.data.total
  } finally { loading.value = false }
}
function reload() { page.value = 1; load() }
function reset() { f.value = { username: '', mac: '', ip: '', type: '', result: null }; reload() }
function onPage(p) { page.value = p; load() }
onMounted(load)
</script>

<style scoped>
.authlog { background: #fff; border-radius: 8px; padding: 16px; }
.bar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; flex-wrap: wrap; gap: 8px; }
.bar .t { font-weight: 600; }
.filters { display: flex; gap: 8px; align-items: center; }
.sub { color: #909399; font-size: 12px; }
.pg { margin-top: 12px; display: flex; justify-content: flex-end; }
</style>
