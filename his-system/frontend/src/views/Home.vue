<template>
  <div>
    <el-row :gutter="16">
      <el-col :span="6" v-for="card in cards" :key="card.title">
        <el-card shadow="hover">
          <div style="display:flex; align-items:center;">
            <i :class="card.icon" style="font-size:36px; color:#409EFF;" />
            <div style="margin-left:16px;">
              <div style="font-size:24px; font-weight:bold;">{{ card.value }}</div>
              <div style="color:#909399;">{{ card.title }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
    <el-card style="margin-top:16px;" shadow="never">
      <div slot="header">发药趋势（近 7 天）</div>
      <div ref="chart" style="height:320px;"></div>
    </el-card>
  </div>
</template>

<script>
import * as echarts from 'echarts'
import { getStats } from '@/api/pharmacy'

export default {
  name: 'Home',
  data() {
    return {
      cards: [
        { title: '待发药处方', value: '-', icon: 'el-icon-s-order' },
        { title: '今日发药单数', value: '-', icon: 'el-icon-s-check' },
        { title: '药品品种数', value: '-', icon: 'el-icon-box' },
        { title: '在院患者', value: '-', icon: 'el-icon-s-custom' }
      ],
      chart: null
    }
  },
  async mounted() {
    try {
      const res = await getStats(7)
      const daily = res.data.daily || []
      this.renderChart(daily)
    } catch (e) { /* 无数据时图表不渲染 */ }
  },
  beforeDestroy() {
    if (this.chart) this.chart.dispose()
  },
  methods: {
    renderChart(daily) {
      this.chart = echarts.init(this.$refs.chart)
      this.chart.setOption({
        tooltip: { trigger: 'axis' },
        legend: { data: ['发药单数', '发药数量'] },
        xAxis: { type: 'category', data: daily.map(d => d.day) },
        yAxis: { type: 'value' },
        series: [
          { name: '发药单数', type: 'bar', data: daily.map(d => d.cnt), itemStyle: { color: '#409EFF' } },
          { name: '发药数量', type: 'line', data: daily.map(d => d.qty), smooth: true, itemStyle: { color: '#67C23A' } }
        ]
      })
    }
  }
}
</script>
