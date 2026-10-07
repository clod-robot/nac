<template>
  <div>
    <el-row :gutter="16">
      <el-col :span="6" :xs="12" :sm="12" :md="6"><el-card><div class="kpi">在线终端<span class="num">{{ online }}</span></div></el-card></el-col>
      <el-col :span="6" :xs="12" :sm="12" :md="6"><el-card><div class="kpi">今日认证<span class="num">{{ todayAuth }}</span></div></el-card></el-col>
      <el-col :span="6" :xs="12" :sm="12" :md="6"><el-card><div class="kpi">短信发送<span class="num">{{ smsCnt }}</span></div></el-card></el-col>
      <el-col :span="6" :xs="12" :sm="12" :md="6"><el-card><div class="kpi">异常拦截<span class="num">{{ blocked }}</span></div></el-card></el-col>
    </el-row>
    <el-row :gutter="16" style="margin-top:16px">
      <el-col :span="12" :xs="24" :sm="24" :md="12"><el-card><div ref="pie" style="height:320px"></div></el-card></el-col>
      <el-col :span="12" :xs="24" :sm="24" :md="12"><el-card><div ref="bar" style="height:320px"></div></el-card></el-col>
    </el-row>

    <!-- 服务器监控 -->
    <el-card style="margin-top:16px" v-loading="loading" shadow="never">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span>服务器资源监控</span>
          <el-tag :type="sseOn ? 'success' : 'info'" size="small">{{ sseOn ? '实时推送中' : '连接中…' }}</el-tag>
        </div>
      </template>

      <el-row :gutter="16">
        <el-col :span="12" :xs="24" :sm="24" :md="12">
          <div class="res-title">CPU</div>
          <div class="res-line">型号：<span class="mono">{{ cpu.model || '—' }}</span></div>
          <div class="res-line">核心：物理 {{ cpu.physicalCores }} 核 / 逻辑 {{ cpu.logicalCores }} 核</div>
          <div class="res-line">使用率：
            <el-progress :percentage="cpu.usage" :stroke-width="14" :color="usageColor(cpu.usage)" style="width:260px;display:inline-block;vertical-align:middle" />
          </div>
        </el-col>
        <el-col :span="12" :xs="24" :sm="24" :md="12">
          <div class="res-title">内存</div>
          <div class="res-line">大小：<span class="mono">{{ fmtBytes(mem.totalBytes) }}</span>
            &nbsp;|&nbsp; 频率：<span class="mono">{{ mem.frequencyMHz ? mem.frequencyMHz + ' MHz' : '—' }}</span></div>
          <div class="res-line">已用 <span class="mono">{{ fmtBytes(mem.usedBytes) }}</span>
            &nbsp;|&nbsp; 缓存 <span class="mono" title="Linux 磁盘缓存，可即时回收">{{ fmtBytes(mem.cachedBytes) }}</span>
            &nbsp;|&nbsp; 可用 <span class="mono">{{ fmtBytes(mem.availableBytes) }}</span></div>
          <div class="res-line">使用率：
            <el-progress :percentage="mem.usagePercent" :stroke-width="14" :color="usageColor(mem.usagePercent)" style="width:260px;display:inline-block;vertical-align:middle" />
          </div>
        </el-col>
      </el-row>

      <el-divider content-position="left">网络接口</el-divider>
      <el-table :data="network" size="small" stripe empty-text="采集到 0 个网卡">
        <el-table-column label="接口" min-width="140">
          <template #default="{ row }">
            <div><b>{{ row.name }}</b></div>
            <div class="sub">{{ row.displayName }}</div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.up ? 'success' : 'info'" size="small">{{ row.up ? 'UP' : 'DOWN' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="协商速率" width="120" align="center">
          <template #default="{ row }">{{ row.speedMbps ? row.speedMbps + ' Mbps' : '—' }}</template>
        </el-table-column>
        <el-table-column label="下载速率 ↓" width="150" align="right">
          <template #default="{ row }"><span class="mono">{{ fmtRate(row.rxRateBps) }}</span></template>
        </el-table-column>
        <el-table-column label="上传速率 ↑" width="150" align="right">
          <template #default="{ row }"><span class="mono">{{ fmtRate(row.txRateBps) }}</span></template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 微服务/中间件状态 -->
    <el-card style="margin-top:16px" shadow="never">
      <template #header><span>服务运行状态</span></template>
      <div class="svc-grid">
        <div v-for="s in services" :key="s.name" class="svc-item">
          <span class="dot" :class="s.online ? 'on' : 'off'"></span>
          <span class="svc-name">{{ s.name }}</span>
          <span class="svc-port">:{{ s.port }}</span>
          <el-tag :type="s.online ? 'success' : 'danger'" size="small" style="margin-left:auto">{{ s.online ? '在线' : '离线' }}</el-tag>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import * as echarts from 'echarts'
import { getMonitorSnapshot, getMonitorStats, onlineList } from '../api/auth'

const online = ref(0)
const todayAuth = ref(0)
const smsCnt = ref(0)
const blocked = ref(0)
const pie = ref(null)
const bar = ref(null)
let pieInst, barInst

const loading = ref(true)
const sseOn = ref(false)
const cpu = reactive({ model: '', physicalCores: 0, logicalCores: 0, usage: 0 })
const mem = reactive({ totalBytes: 0, usedBytes: 0, cachedBytes: 0, availableBytes: 0, usagePercent: 0, frequencyMHz: null })
const network = ref([])
const services = ref([])

let es = null

async function loadSnapshot() {
  try {
    const r = await getMonitorSnapshot()
    const d = r.data || {}
    Object.assign(cpu, d.cpu || {})
    Object.assign(mem, d.memory || {})
    network.value = (d.network || []).map(n => ({ ...n }))
    services.value = d.services || []
  } finally {
    loading.value = false
  }
}

function pad(n) { return n < 10 ? '0' + n : '' + n }

// 今日认证/短信/拦截 + 饼图/柱图（真实数据）
async function loadStats() {
  try {
    const r = await getMonitorStats()
    const d = r.data || {}
    todayAuth.value = d.todayAuth ?? 0
    smsCnt.value = d.smsCnt ?? 0
    blocked.value = d.blocked ?? 0

    const pieData = (d.pie || []).map(p => ({ name: p.name, value: Number(p.value) || 0 }))
    if (pieData.length === 0) pieData.push({ name: '暂无数据', value: 1 })
    pieInst && pieInst.setOption({ series: [{ data: pieData }] })

    const tmap = {}
    ;(d.trend || []).forEach(t => { tmap[t.day] = Number(t.value) || 0 })
    const days = [], vals = []
    const now = new Date()
    for (let i = 6; i >= 0; i--) {
      const dt = new Date(now); dt.setDate(now.getDate() - i)
      const key = dt.getFullYear() + '-' + pad(dt.getMonth() + 1) + '-' + pad(dt.getDate())
      days.push((dt.getMonth() + 1) + '/' + dt.getDate())
      vals.push(tmap[key] || 0)
    }
    barInst && barInst.setOption({ xAxis: { data: days }, series: [{ data: vals }] })
  } catch (e) { /* 接口异常时保持 0，不阻断页面 */ }
}

// 在线终端数（每次进入仪表盘都从后端拉取）
async function loadOnline() {
  try {
    const r = await onlineList({ page: 1, size: 1 })
    online.value = (r.data && r.data.total) ? r.data.total : 0
  } catch (e) { online.value = 0 }
}

function openSSE() {
  const token = localStorage.getItem('nac_token') || ''
  const url = `${window.location.origin}/api/auth/monitor/stream?token=${encodeURIComponent(token)}`
  es = new EventSource(url)
  es.addEventListener('metrics', (ev) => {
    sseOn.value = true
    let d
    try { d = JSON.parse(ev.data) } catch (e) { return }
    if (typeof d.cpu === 'number') cpu.usage = d.cpu
    if (typeof d.mem === 'number') mem.usagePercent = d.mem
    if (Array.isArray(d.net)) {
      const map = {}
      d.net.forEach(n => { map[n.name] = n })
      network.value.forEach(n => {
        const up = map[n.name]
        if (up) { n.rxRateBps = up.rx; n.txRateBps = up.tx }
      })
    }
  })
  es.onerror = () => { sseOn.value = false }
}

function fmtBytes(b) {
  if (!b) return '0 B'
  const u = ['B', 'KB', 'MB', 'GB', 'TB']
  let i = 0; let v = b
  while (v >= 1024 && i < u.length - 1) { v /= 1024; i++ }
  return v.toFixed(i === 0 ? 0 : 1) + ' ' + u[i]
}
function fmtRate(bps) {
  if (!bps) return '0 KB/s'
  let v = bps / 1024
  if (v < 1024) return v.toFixed(1) + ' KB/s'
  return (v / 1024).toFixed(2) + ' MB/s'
}
function usageColor(v) { return v >= 80 ? '#F56C6C' : v >= 60 ? '#E6A23C' : '#67C23A' }

onMounted(() => {
  pieInst = echarts.init(pie.value)
  pieInst.setOption({
    title: { text: '认证方式分布', left: 'center' },
    tooltip: { trigger: 'item' },
    legend: { bottom: 0 },
    series: [{ type: 'pie', radius: ['40%', '70%'], data: [] }]
  })
  barInst = echarts.init(bar.value)
  barInst.setOption({
    title: { text: '近 7 日认证趋势', left: 'center' },
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: [] },
    yAxis: { type: 'value' },
    series: [{ type: 'bar', data: [], itemStyle: { color: '#409EFF' } }]
  })
  window.addEventListener('resize', resize)
  loadSnapshot()
  loadStats()
  loadOnline()
  openSSE()
})
function resize() { pieInst && pieInst.resize(); barInst && barInst.resize() }
onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  pieInst && pieInst.dispose(); barInst && barInst.dispose()
  if (es) { es.close(); es = null }
})
</script>

<style scoped>
.kpi { font-size: 14px; color: #909399; }
.num { display: block; font-size: 28px; color: #303133; font-weight: 600; margin-top: 8px; }
.res-title { font-weight: 600; color: #303133; margin-bottom: 8px; }
.res-line { margin: 6px 0; color: #606266; font-size: 13px; }
.mono { font-family: Consolas, Menlo, monospace; color: #303133; }
.sub { color: #909399; font-size: 12px; }
.svc-grid { display: flex; flex-wrap: wrap; gap: 10px; }
.svc-item { display: flex; align-items: center; gap: 8px; min-width: 200px; padding: 8px 12px; border: 1px solid #ebeef5; border-radius: 6px; }
.dot { width: 10px; height: 10px; border-radius: 50%; }
.dot.on { background: #67C23A; box-shadow: 0 0 6px #67C23A; }
.dot.off { background: #F56C6C; }
.svc-name { font-weight: 600; }
.svc-port { color: #909399; font-size: 12px; font-family: Consolas, monospace; }
</style>
