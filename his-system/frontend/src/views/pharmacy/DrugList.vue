<template>
  <el-card shadow="never">
    <div slot="header">药品目录</div>
    <el-table :data="list" v-loading="loading" border>
      <el-table-column prop="drugCode" label="编码" width="100" />
      <el-table-column prop="drugName" label="药品名称" />
      <el-table-column prop="specification" label="规格" width="140" />
      <el-table-column prop="manufacturer" label="生产厂家" width="140" />
      <el-table-column prop="dosageForm" label="剂型" width="90" />
      <el-table-column prop="price" label="单价(元)" width="100" />
      <el-table-column prop="unit" label="单位" width="70" />
    </el-table>
    <el-pagination style="margin-top:12px; text-align:right;"
      background layout="prev, pager, next, total"
      :total="total" :page-size="size" :current-page.sync="page" @current-change="load" />
  </el-card>
</template>

<script>
import { listDrugs } from '@/api/pharmacy'
export default {
  name: 'DrugList',
  data() { return { list: [], total: 0, page: 1, size: 20, loading: false } },
  created() { this.load() },
  methods: {
    async load() {
      this.loading = true
      try {
        const res = await listDrugs({ page: this.page, size: this.size })
        this.list = res.data.list
        this.total = res.data.total
      } finally { this.loading = false }
    }
  }
}
</script>
