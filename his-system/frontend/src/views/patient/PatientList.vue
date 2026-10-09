<template>
  <el-card shadow="never">
    <div slot="header">
      <span>患者档案</span>
      <el-button type="primary" size="mini" style="float:right;" icon="el-icon-plus" @click="dialogVisible=true">新增患者</el-button>
    </div>
    <el-table :data="list" v-loading="loading" border>
      <el-table-column prop="patientNo" label="病历号" width="160" />
      <el-table-column prop="name" label="姓名" width="100" />
      <el-table-column prop="gender" label="性别" width="70" />
      <el-table-column prop="idCard" label="身份证号" width="180" />
      <el-table-column prop="phone" label="手机号" width="130" />
      <el-table-column prop="allergyHistory" label="过敏史" />
    </el-table>
    <el-pagination style="margin-top:12px; text-align:right;" background layout="prev, pager, next, total"
      :total="total" :page-size="size" :current-page.sync="page" @current-change="load" />

    <el-dialog title="新增患者" :visible.sync="dialogVisible" width="480px">
      <el-form :model="form" label-width="80px" size="small">
        <el-form-item label="姓名"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="身份证"><el-input v-model="form.idCard" maxlength="18" /></el-form-item>
        <el-form-item label="手机号"><el-input v-model="form.phone" maxlength="11" /></el-form-item>
        <el-form-item label="性别">
          <el-radio-group v-model="form.gender"><el-radio label="男" /><el-radio label="女" /></el-radio-group>
        </el-form-item>
        <el-form-item label="过敏史"><el-input v-model="form.allergyHistory" /></el-form-item>
      </el-form>
      <div slot="footer">
        <el-button @click="dialogVisible=false">取消</el-button>
        <el-button type="primary" @click="create">保存</el-button>
      </div>
    </el-dialog>
  </el-card>
</template>

<script>
import request from '@/utils/request'
export default {
  name: 'PatientList',
  data() { return { list: [], total: 0, page: 1, size: 20, loading: false, dialogVisible: false, form: {} } },
  created() { this.load() },
  methods: {
    async load() {
      this.loading = true
      try {
        const res = await request.get('/api/patient/list', { params: { page: this.page, size: this.size } })
        this.list = res.data.list
        this.total = res.data.total
      } finally { this.loading = false }
    },
    async create() {
      await request.post('/api/patient', this.form)
      this.$message.success('新增成功')
      this.dialogVisible = false
      this.form = {}
      this.load()
    }
  }
}
</script>
