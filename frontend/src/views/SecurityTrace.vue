<template>
  <div class="trace-page">
    <!-- 顶部：时间窗 + 多维度筛选 -->
    <div class="bar">
      <span class="t">安全事件溯源</span>
      <div class="filters">
        <el-date-picker v-model="range" type="datetimerange" range-separator="至"
          start-placeholder="开始时间" end-placeholder="结束时间"
          format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DD HH:mm"
          style="width:340px" />
        <el-select v-model="f.type" placeholder="认证类型" clearable style="width:130px">
          <el-option label="登录 login" value="login" />
          <el-option label="Portal" value="portal" />
          <el-option label="RADIUS" value="radius" />
        </el-select>
        <el-select v-model="f.result" placeholder="结果" clearable style="width:100px">
          <el-option label="成功" :value="1" />
          <el-option label="失败" :value="0" />
        </el-select>
        <el-input v-model="f.username" placeholder="账号/手机号" clearable style="width:150px" @keyup.enter="reloadAll" />
        <el-input v-model="f.mac" placeholder="MAC" clearable style="width:150px" @keyup.enter="reloadAll" />
        <el-input v-model="f.ip" placeholder="IP" clearable style="width:130px" @keyup.enter="reloadAll" />
        <el-button :icon="Search" type="primary" @click="reloadAll">查询</el-button>
        <el-button :icon="Refresh" @click="reset">重置</el-button>
        <el-button :icon="Download" type="warning" :loading="exporting" @click="onExport">导出 CSV</el-button>
      </div>
    </div>

    <!-- 统计图表 -->
    <el-row :gutter="12" v-loading="statLoading">
      <el-col :span="24"><el-card shadow="never"><div ref="trendEl" class="chart chart-lg"></div></el-card></el-col>
    </el-row>
    <el-row :gutter="12" style="margin-top:12px">
      <el-col :span="8" :xs="24" :sm="24" :md="8"><el-card shadow="never"><div ref="typeEl" class="chart"></div></el-card></el-col>
      <el-col :span="8" :xs="24" :sm="24" :md="8"><el-card shadow="never"><div ref="ssidEl" class="chart"></div></el-card></el-col>
      <el-col :span="8" :xs="24" :sm="24" :md="8"><el-card shadow="never"><div ref="topUserEl" class="chart"></div></el-card></el-col>
    </el-row>
    <el-row :gutter="12" style="margin-top:12px">
      <el-col :span="12" :xs="24" :sm="24" :md="12"><el-card shadow="never"><div ref="topMacEl" class="chart"></div></el-card></el-col>
      <el-col :span="12" :xs="24" :sm="24" :md="12"><el-card shadow="never"><div ref="topNasEl" class="chart"></div></el-card></el-col>
    </el-row>

    <!-- 事件列表 -->
    <el-card shadow="never" style="margin-top:12px" v-loading="listLoading">
      <template #header>
        <div class="list-head">
          <span>事件列表</span>
          <span class="tip">点击行可查看该事件的接入终端与责任人</span>
        </div>
      </template>
      <el-table :data="list" border @row-click="openDetail" highlight-current-row style="cursor:pointer">
        <el-table-column label="时间" width="150">
          <template #default="{ row }">{{ fmt(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="typeTag(row.authType)">{{ row.authType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="账号/手机号" min-width="130">
          <template #default="{ row }">{{ row.username || row.phone || '-' }}</template>
        </el-table-column>
        <el-table-column prop="mac" label="MAC" width="150" show-overflow-tooltip />
        <el-table-column label="IP / NAS" min-width="150">
          <template #default="{ row }">
            <div>{{ row.ip || '-' }}</div>
            <div v-if="row.nasIp" class="sub">{{ row.nasIp }}</div>
            <div v-if="row.nasName" class="sub">{{ row.nasName }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="ssid" label="SSID" width="110" show-overflow-tooltip />
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
        <el-pagination background layout="total, prev, pager, next" :total="total"
          :current-page="page" :page-size="size" @current-change="onPage" />
      </div>
    </el-card>

    <!-- 溯源详情抽屉：接入终端 + 责任人 -->
    <el-drawer v-model="drawer" title="事件溯源详情" size="480px">
      <div v-if="cur" class="detail">
        <el-divider content-position="left">事件信息</el-divider>
        <div class="kv"><span class="k">时间</span><span class="v">{{ fmt(cur.createTime) }}</span></div>
        <div class="kv"><span class="k">认证类型</span><span class="v">{{ cur.authType }}</span></div>
        <div class="kv"><span class="k">结果</span>
          <el-tag size="small" :type="cur.result === 1 ? 'success' : 'danger'">
            {{ cur.result === 1 ? '成功' : '失败' }}
          </el-tag>
        </div>
        <div class="kv"><span class="k">说明</span><span class="v">{{ cur.message || '-' }}</span></div>

        <el-divider content-position="left">接入终端</el-divider>
        <div class="kv"><span class="k">MAC</span><span class="v mono">{{ cur.mac || '-' }}</span></div>
        <div class="kv"><span class="k">终端 IP</span><span class="v mono">{{ cur.ip || '-' }}</span></div>
        <div class="kv"><span class="k">NAS IP</span><span class="v mono">{{ cur.nasIp || '-' }}</span></div>
        <div class="kv"><span class="k">NAS 名称</span><span class="v">{{ cur.nasName || '-' }}</span></div>
        <div class="kv"><span class="k">SSID</span><span class="v">{{ cur.ssid || '-' }}</span></div>

        <el-divider content-position="left">责任人</el-divider>
        <div v-loading="userLoading">
          <template v-if="user">
            <div class="kv"><span class="k">姓名</span><span class="v">{{ user.realName || '-' }}</span></div>
            <div class="kv"><span class="k">账号</span><span class="v">{{ user.username }}</span></div>
            <div class="kv"><span class="k">部门</span><span class="v">{{ user.dept || '-' }}</span></div>
            <div class="kv"><span class="k">手机号</span><span class="v mono">{{ user.phoneBlind || '-' }}</span></div>
            <div class="kv"><span class="k">角色</span>
              <el-tag size="small" :type="user.roleCode === 'admin' ? 'warning' : 'info'">{{ user.roleCode }}</el-tag>
            </div>
            <div class="kv"><span class="k">账号状态</span>
              <el-tag size="small" :type="user.status === 1 ? 'success' : 'danger'">
                {{ user.status === 1 ? '启用' : '禁用' }}
              </el-tag>
            </div>
          </template>
          <el-empty v-else-if="!userLoading" description="未在账号表中找到对应责任人（可能为临时账号或未注册设备）" :image-size="80" />
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import * as echarts from 'echarts'
import { Refresh, Search, Download } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { authLogList, traceStats, traceUser, traceExport } from '../api/auth'
import { fmtTime } from '../utils/format'

const range = ref(null)
const f = ref({ type: '', result: null, username: '', mac: '', ip: '' })

const list = ref([]); const total = ref(0); const page = ref(1); const size = ref(20)
const listLoading = ref(false)
const statLoading = ref(false)
const exporting = ref(false)

const drawer = ref(false)
const cur = ref(null)
const user = ref(null)
const userLoading = ref(false)

const trendEl = ref(null), typeEl = ref(null), ssidEl = ref(null)
const topUserEl = ref(null), topMacEl = ref(null), topNasEl = ref(null)
let trendInst, typeInst, ssidInst, topUserInst, topMacInst, topNasInst

const fmt = fmtTime
const typeTag = (t) => ({ login: '', portal: 'warning', radius: 'success' }[t] || 'info')

function timeParams() {
  const p = {}
  if (range.value && range.value[0]) p.startTime = range.value[0]
  if (range.value && range.value[1]) p.endTime = range.value[1]
  return p
}

async function loadList() {
  listLoading.value = true
  try {
    const params = { page: page.value, size: size.value, ...timeParams() }
    if (f.value.type) params.type = f.value.type
    if (f.value.result !== null && f.value.result !== '') params.result = f.value.result
    if (f.value.username) params.username = f.value.username
    if (f.value.mac) params.mac = f.value.mac
    if (f.value.ip) params.ip = f.value.ip
    const r = await authLogList(params)
    list.value = r.data.list; total.value = r.data.total
  } finally { listLoading.value = false }
}

async function loadStats() {
  statLoading.value = true
  try {
    const r = await traceStats(timeParams())
    const d = r.data || {}
    renderTrend(d.trend || [])
    renderPie(typeInst, '认证类型分布', (d.authTypeDist || []).map(x => ({ name: x.name, value: Number(x.value) || 0 })))
    renderPie(ssidInst, 'NAS / SSID 分布', (d.ssidDist || []).map(x => ({ name: x.name, value: Number(x.value) || 0 })))
    renderBar(topUserInst, 'Top 失败用户', d.topFailUser || [])
    renderBar(topMacInst, 'Top 失败终端（MAC）', d.topFailMac || [])
    renderBar(topNasInst, 'Top 失败 NAS', d.topFailNas || [])
  } finally { statLoading.value = false }
}

function renderTrend(rows) {
  const map = {}
  rows.forEach(r => { map[r.bucket] = { s: Number(r.success) || 0, f: Number(r.fail) || 0 } })
  // 默认时间窗：近 24 小时（若用户已选时间，按其范围）
  let st, et
  if (range.value && range.value[0] && range.value[1]) {
    st = new Date(range.value[0].replace(' ', 'T'))
    et = new Date(range.value[1].replace(' ', 'T'))
  } else {
    et = new Date(); st = new Date(et.getTime() - 24 * 3600 * 1000)
  }
  // 对齐到整点
  st.setMinutes(0, 0, 0); et.setMinutes(0, 0, 0)
  const buckets = [], success = [], fail = []
  const maxPoints = 200
  const totalHours = Math.floor((et - st) / 3600000) + 1
  const step = Math.max(1, Math.ceil(totalHours / maxPoints))
  for (let t = new Date(st), i = 0; t <= et; t = new Date(t.getTime() + step * 3600000), i++) {
    const key = t.getFullYear() + '-' + pad(t.getMonth() + 1) + '-' + pad(t.getDate()) + ' ' + pad(t.getHours()) + ':00'
    const label = (t.getMonth() + 1) + '/' + t.getDate() + ' ' + pad(t.getHours()) + ':00'
    buckets.push(label)
    const v = map[key] || { s: 0, f: 0 }
    success.push(v.s); fail.push(v.f)
  }
  trendInst && trendInst.setOption({
    title: { text: '认证成功/失败趋势', left: 'center' },
    tooltip: { trigger: 'axis' },
    legend: { bottom: 0 },
    grid: { left: 50, right: 20, top: 40, bottom: 50 },
    xAxis: { type: 'category', data: buckets, axisLabel: { interval: Math.max(0, Math.floor(buckets.length / 12)) } },
    yAxis: { type: 'value' },
    series: [
      { name: '成功', type: 'line', smooth: true, data: success, itemStyle: { color: '#67C23A' }, areaStyle: { opacity: 0.15 } },
      { name: '失败', type: 'line', smooth: true, data: fail, itemStyle: { color: '#F56C6C' }, areaStyle: { opacity: 0.15 } }
    ]
  })
}

function renderPie(inst, title, data) {
  if (!inst) return
  const d = (data && data.length) ? data : [{ name: '暂无数据', value: 1 }]
  inst.setOption({
    title: { text: title, left: 'center' },
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, type: 'scroll' },
    series: [{ type: 'pie', radius: ['40%', '65%'], center: ['50%', '55%'], data: d,
      label: { formatter: '{b}\n{c} ({d}%)' } }]
  })
}

function renderBar(inst, title, rows) {
  if (!inst) return
  const names = rows.map(r => r.name).reverse()
  const values = rows.map(r => Number(r.value) || 0).reverse()
  inst.setOption({
    title: { text: title, left: 'center' },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 10, right: 30, top: 40, bottom: 10, containLabel: true },
    xAxis: { type: 'value' },
    yAxis: { type: 'category', data: names, axisLabel: { width: 110, overflow: 'truncate' } },
    series: [{ type: 'bar', data: values, itemStyle: { color: '#F56C6C' },
      label: { show: true, position: 'right' } }]
  })
}

function pad(n) { return n < 10 ? '0' + n : '' + n }

async function openDetail(row) {
  cur.value = row
  drawer.value = true
  user.value = null
  userLoading.value = true
  try {
    const params = {}
    if (row.username) params.username = row.username
    if (row.phone) params.phone = row.phone
    const r = await traceUser(params)
    user.value = (r.data && r.data.found) ? r.data.user : null
  } catch (e) {
    user.value = null
  } finally { userLoading.value = false }
}

function reloadAll() { page.value = 1; loadList(); loadStats() }
function reset() {
  range.value = null
  f.value = { type: '', result: null, username: '', mac: '', ip: '' }
  reloadAll()
}
function onPage(p) { page.value = p; loadList() }

/** 导出当前筛选条件下的完整日志为 CSV（公安电子证据留存，含责任人） */
async function onExport() {
  exporting.value = true
  try {
    const params = { ...timeParams() }
    if (f.value.type) params.type = f.value.type
    if (f.value.result !== null && f.value.result !== '') params.result = f.value.result
    if (f.value.username) params.username = f.value.username
    if (f.value.mac) params.mac = f.value.mac
    if (f.value.ip) params.ip = f.value.ip
    const r = await traceExport(params)
    const blob = new Blob([r.data], { type: 'text/csv;charset=UTF-8' })
    // 优先取后端 Content-Disposition 中的文件名，兜底用本地时间戳
    const cd = r.headers && (r.headers['content-disposition'] || r.headers['Content-Disposition'])
    let fname = 'SecurityTrace_' + new Date().toISOString().replace(/[-:T]/g, '').slice(0, 14) + '.csv'
    if (cd) {
      const m = /filename\*?=(?:UTF-8'')?"?([^";]+)/i.exec(cd)
      if (m) fname = decodeURIComponent(m[1])
    }
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = fname
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
    ElMessage.success('已导出 ' + fname)
  } catch (e) {
    ElMessage.error('导出失败：' + (e.message || '未知错误'))
  } finally { exporting.value = false }
}
function resize() {
  [trendInst, typeInst, ssidInst, topUserInst, topMacInst, topNasInst].forEach(i => i && i.resize())
}

onMounted(() => {
  trendInst = echarts.init(trendEl.value)
  typeInst = echarts.init(typeEl.value)
  ssidInst = echarts.init(ssidEl.value)
  topUserInst = echarts.init(topUserEl.value)
  topMacInst = echarts.init(topMacEl.value)
  topNasInst = echarts.init(topNasEl.value)
  window.addEventListener('resize', resize)
  reloadAll()
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  ;[trendInst, typeInst, ssidInst, topUserInst, topMacInst, topNasInst].forEach(i => i && i.dispose())
})
</script>

<style scoped>
.trace-page { display: flex; flex-direction: column; }
.bar { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 8px; background: #fff; padding: 12px 16px; border-radius: 8px; margin-bottom: 12px; }
.bar .t { font-weight: 600; font-size: 15px; white-space: nowrap; }
.filters { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.chart { height: 260px; }
.chart-lg { height: 300px; }
.list-head { display: flex; align-items: center; justify-content: space-between; }
.tip { color: #909399; font-size: 12px; }
.sub { color: #909399; font-size: 12px; }
.pg { margin-top: 12px; display: flex; justify-content: flex-end; }
.detail .kv { display: flex; margin: 8px 0; font-size: 13px; line-height: 1.6; }
.detail .k { width: 80px; flex-shrink: 0; color: #909399; }
.detail .v { flex: 1; color: #303133; word-break: break-all; }
.mono { font-family: Consolas, Menlo, monospace; }
</style>
