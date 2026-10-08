<template>
  <div class="online" v-loading="loading">
    <div class="bar">
      <span class="t">在线终端管理</span>
      <span class="tip">数据来自 RADIUS 计费会话（sys_online_session）</span>
    </div>
    <div class="filters">
      <el-input v-model="f.username" placeholder="用户" clearable style="width:160px" @keyup.enter="reload" />
      <el-input v-model="f.mac" placeholder="MAC地址" clearable style="width:180px" @keyup.enter="reload" />
      <el-input v-model="f.ip" placeholder="终端IP" clearable style="width:160px" @keyup.enter="reload" />
      <el-button :icon="Search" type="primary" @click="reload">查询</el-button>
      <el-button :icon="Refresh" @click="reset">重置</el-button>
    </div>
    <el-table :data="list" border>
      <el-table-column prop="acctSessionId" label="会话ID" min-width="160" show-overflow-tooltip />
      <el-table-column prop="usernameMask" label="用户" width="120" />
      <el-table-column prop="mac" label="MAC" width="150" />
      <el-table-column prop="nasIp" label="NAS IP" width="130" />
      <el-table-column prop="framedIp" label="终端IP" width="130" />
      <el-table-column prop="vlanId" label="VLAN" width="70" />
      <el-table-column label="上线时间" width="150">
        <template #default="{ row }">{{ fmt(row.startTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="110" fixed="right">
        <template #default="{ row }">
          <el-popconfirm title="确认强制该终端下线？" @confirm="offline(row)">
            <template #reference><el-button size="small" type="danger" link>强制下线</el-button></template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>
    <div class="pg">
      <el-pagination background layout="total, prev, pager, next" :total="total" :current-page="page" :page-size="size" @current-change="onPage" />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import { onlineList, onlineDelete } from '../api/auth'

const list = ref([]); const total = ref(0); const page = ref(1); const size = ref(20)
const loading = ref(false)
const f = ref({ username: '', mac: '', ip: '' })
const pad = (n) => String(n).padStart(2, '0')
const fmt = (t) => { if (!t) return '-'; const d = new Date(t); return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}` }

async function load() {
  loading.value = true
  try {
    const params = { page: page.value, size: size.value }
    if (f.value.username) params.username = f.value.username
    if (f.value.mac) params.mac = f.value.mac
    if (f.value.ip) params.ip = f.value.ip
    const r = await onlineList(params)
    list.value = r.data.list; total.value = r.data.total
  } finally { loading.value = false }
}
async function offline(row) {
  await onlineDelete(row.id)
  ElMessage.success('已下线')
  load()
}
function reload() { page.value = 1; load() }
function reset() { f.value = { username: '', mac: '', ip: '' }; reload() }
function onPage(p) { page.value = p; load() }
onMounted(load)
</script>

<style scoped>
.online { background: #fff; border-radius: 8px; padding: 16px; }
.bar { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; }
.filters { display: flex; gap: 8px; align-items: center; margin-bottom: 12px; flex-wrap: wrap; }
.bar .t { font-weight: 600; }
.tip { color: #909399; font-size: 12px; flex: 1; }
.pg { margin-top: 12px; display: flex; justify-content: flex-end; }
</style>
