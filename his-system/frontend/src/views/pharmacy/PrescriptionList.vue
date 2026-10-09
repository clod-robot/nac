<template>
  <div>
    <el-card shadow="never">
      <div slot="header">
        <span>待发药处方</span>
        <el-button style="float:right;" size="mini" icon="el-icon-refresh" @click="loadList">刷新</el-button>
      </div>
      <el-table :data="list" v-loading="loading" border stripe @row-click="showDetail" highlight-current-row>
        <el-table-column prop="prescriptionNo" label="处方号" width="180" />
        <el-table-column prop="patientName" label="患者" width="100" />
        <el-table-column prop="doctorName" label="开方医生" width="100" />
        <el-table-column prop="type" label="类型" width="80">
          <template slot-scope="s">
            <el-tag size="mini">{{ typeLabel(s.row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="totalAmount" label="金额(元)" width="100" />
        <el-table-column prop="createTime" label="开方时间" width="160" />
        <el-table-column label="操作" width="180">
          <template slot-scope="s">
            <el-button size="mini" type="text" @click.stop="showDetail(s.row)">审核/发药</el-button>
            <el-button size="mini" type="text" style="color:#67C23A" @click.stop="quickDispense(s.row)" v-permission="['pharmacist','admin']">一键发药</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && list.length === 0" description="暂无待发药处方" />
    </el-card>

    <!-- 处方详情 + 审核 + 发药抽屉 -->
    <el-drawer :visible.sync="detailVisible" title="处方详情" size="50%">
      <div v-if="detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="处方号">{{ detail.prescriptionNo }}</el-descriptions-item>
          <el-descriptions-item label="患者">{{ detail.patientName }}</el-descriptions-item>
          <el-descriptions-item label="开方医生">{{ detail.doctorName }}</el-descriptions-item>
          <el-descriptions-item label="诊断">{{ detail.diagnosis }}</el-descriptions-item>
          <el-descriptions-item label="总金额">{{ detail.totalAmount }} 元</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="detail.stockSufficient ? 'success' : 'danger'" size="mini">
              {{ detail.stockSufficient ? '库存充足' : '库存不足' }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>

        <h4>药品明细</h4>
        <el-table :data="detail.items" border size="mini">
          <el-table-column prop="drugName" label="药品" />
          <el-table-column prop="specification" label="规格" width="120" />
          <el-table-column prop="quantity" label="数量" width="80" />
          <el-table-column prop="unit" label="单位" width="60" />
          <el-table-column prop="usage" label="用法用量" />
          <el-table-column label="当前库存" width="90">
            <template slot-scope="s">
              <span :style="{ color: s.row.stockQuantity >= s.row.quantity ? '#67C23A' : '#F56C6C' }">
                {{ s.row.stockQuantity }}
              </span>
            </template>
          </el-table-column>
        </el-table>

        <div style="margin-top:20px; text-align:right;">
          <el-input v-model="reviewOpinion" placeholder="审核意见（驳回时必填）" size="small" style="width:280px; margin-right:8px;" />
          <el-button type="warning" size="small" @click="review(false)">驳回</el-button>
          <el-button type="success" size="small" @click="review(true)">审核通过</el-button>
          <el-button type="primary" size="small" :disabled="!detail.stockSufficient" :loading="dispensing" @click="doDispense" v-permission="['pharmacist','admin']">确认发药</el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script>
import { listPrescriptions, getPrescriptionDetail, reviewPrescription, dispense } from '@/api/pharmacy'

export default {
  name: 'PrescriptionList',
  data() {
    return { list: [], loading: false, detailVisible: false, detail: null, dispensing: false, reviewOpinion: '' }
  },
  created() { this.loadList() },
  methods: {
    typeLabel(t) { return { RX: '西药', ZCY: '中成药', ZY: '中药' }[t] || t },
    async loadList() {
      this.loading = true
      try {
        const res = await listPrescriptions({ limit: 50 })
        this.list = res.data || []
      } finally { this.loading = false }
    },
    async showDetail(row) {
      const res = await getPrescriptionDetail(row.id)
      this.detail = res.data
      this.reviewOpinion = ''
      this.detailVisible = true
    },
    async review(approved) {
      await reviewPrescription(this.detail.id, approved, this.reviewOpinion)
      this.$message.success(approved ? '审核通过' : '已驳回')
      this.detailVisible = false
      this.loadList()
    },
    async doDispense() {
      this.$confirm('确认发药？发药后库存将扣减并记录发药流水。', '提示', { type: 'warning' }).then(async () => {
        this.dispensing = true
        try {
          await dispense(this.detail.id, '1')
          this.$message.success('发药成功')
          this.detailVisible = false
          this.loadList()
        } finally { this.dispensing = false }
      }).catch(() => {})
    },
    async quickDispense(row) {
      this.$confirm('确认发放处方 ' + row.prescriptionNo + ' ？', '提示', { type: 'warning' }).then(async () => {
        try {
          await dispense(row.id, '1')
          this.$message.success('发药成功')
          this.loadList()
        } catch (e) { /* 拦截器已提示 */ }
      }).catch(() => {})
    }
  }
}
</script>
