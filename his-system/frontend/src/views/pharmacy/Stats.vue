<template>
  <div>
    <el-card shadow="never">
      <div slot="header">
        <span>发药统计</span>
        <el-radio-group v-model="days" size="mini" style="float:right;" @change="load">
          <el-radio-button :label="3">近3天</el-radio-button>
          <el-radio-button :label="7">近7天</el-radio-button>
          <el-radio-button :label="30">近30天</el-radio-button>
        </el-radio-group>
      </div>
      <el-row :gutter="16">
        <el-col :span="14"><div ref="dailyChart" style="height:340px;"></div></el-col>
        <el-col :span="10"><div ref="topChart" style="height:340px;"></div></el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script>
import * as echarts from 'echarts'
import { getStats } from '@/api/pharmacy'

export default {
  name: 'PharmacyStats',
  data() { return { days: 7, dailyChart: null, topChart: null } },
  mounted() { this.load() },
  beforeDestroy() {
    if (this.dailyChart) this.dailyChart.dispose()
    if (this.topChart) this.topChart.dispose()
  },
  methods: {
    async load() {
      const res = await getStats(this.days)
      const daily = res.data.daily || []
      const top = res.data.topDrugs || []
      this.$nextTick(() => this.render(daily, top))
    },
    render(daily, top) {
      if (!this.dailyChart) this.dailyChart = echarts.init(this.$refs.dailyChart)
      this.dailyChart.setOption({
        title: { text: '每日发药趋势', left: 'center', textStyle: { fontSize: 14 } },
        tooltip: { trigger: 'axis' },
        legend: { data: ['发药单数', '发药数量'], bottom: 0 },
        grid: { top: 50, bottom: 40 },
        xAxis: { type: 'category', data: daily.map(d => d.day) },
        yAxis: { type: 'value' },
        series: [
          { name: '发药单数', type: 'bar', data: daily.map(d => d.cnt), itemStyle: { color: '#409EFF' } },
          { name: '发药数量', type: 'line', data: daily.map(d => d.qty), smooth: true, itemStyle: { color: '#E6A23C' } }
        ]
      })
      if (!this.topChart) this.topChart = echarts.init(this.$refs.topChart)
      this.topChart.setOption({
        title: { text: '发药 TOP10 药品', left: 'center', textStyle: { fontSize: 14 } },
        tooltip: { trigger: 'item' },
        series: [{
          type: 'pie', radius: ['40%', '70%'], center: ['50%', '55%'],
          data: top.map(t => ({ name: t.name, value: t.qty })),
          label: { formatter: '{b}: {c}' }
        }]
      })
    }
  }
}
</script>
