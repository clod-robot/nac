<template>
  <div>
    <el-row :gutter="16">
      <el-col :span="6"><el-card><div class="kpi">在线终端<span class="num">{{ online }}</span></div></el-card></el-col>
      <el-col :span="6"><el-card><div class="kpi">今日认证<span class="num">{{ todayAuth }}</span></div></el-card></el-col>
      <el-col :span="6"><el-card><div class="kpi">短信发送<span class="num">{{ smsCnt }}</span></div></el-card></el-col>
      <el-col :span="6"><el-card><div class="kpi">异常拦截<span class="num">{{ blocked }}</span></div></el-card></el-col>
    </el-row>
    <el-row :gutter="16" style="margin-top:16px">
      <el-col :span="12"><el-card><div ref="pie" style="height:320px"></div></el-card></el-col>
      <el-col :span="12"><el-card><div ref="bar" style="height:320px"></div></el-card></el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import * as echarts from 'echarts'

const online = ref(128)
const todayAuth = ref(342)
const smsCnt = ref(87)
const blocked = ref(5)
const pie = ref(null)
const bar = ref(null)
let pieInst, barInst

onMounted(() => {
  pieInst = echarts.init(pie.value)
  pieInst.setOption({
    title: { text: '认证方式分布', left: 'center' },
    tooltip: { trigger: 'item' },
    series: [{ type: 'pie', radius: ['40%', '70%'], data: [
      { value: 210, name: '账号密码' }, { value: 90, name: '短信验证码' }, { value: 42, name: 'Portal' }
    ] }]
  })
  barInst = echarts.init(bar.value)
  barInst.setOption({
    title: { text: '近 7 日认证趋势', left: 'center' },
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: ['周一','周二','周三','周四','周五','周六','周日'] },
    yAxis: { type: 'value' },
    series: [{ type: 'bar', data: [120, 200, 150, 80, 70, 110, 130], itemStyle: { color: '#409EFF' } }]
  })
  window.addEventListener('resize', resize)
})
function resize() { pieInst && pieInst.resize(); barInst && barInst.resize() }
onBeforeUnmount(() => { window.removeEventListener('resize', resize); pieInst && pieInst.dispose(); barInst && barInst.dispose() })
</script>

<style scoped>
.kpi { font-size: 14px; color: #909399; }
.num { display: block; font-size: 28px; color: #303133; font-weight: 600; margin-top: 8px; }
</style>
