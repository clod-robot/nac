<template>
  <el-card shadow="never">
    <div slot="header">
      <span>电子病历</span>
      <el-button type="primary" size="mini" style="float:right;" icon="el-icon-edit" @click="dialogVisible=true">新建病历</el-button>
    </div>
    <el-table :data="list" border v-loading="loading">
      <el-table-column prop="recordNo" label="病历号" width="180" />
      <el-table-column prop="patientName" label="患者" width="100" />
      <el-table-column prop="doctorName" label="医生" width="100" />
      <el-table-column prop="chiefComplaint" label="主诉" />
      <el-table-column prop="diagnosis" label="诊断" />
      <el-table-column prop="status" label="状态" width="90">
        <template slot-scope="s"><el-tag size="mini">{{ s.row.status }}</el-tag></template>
      </el-table-column>
      <el-table-column label="操作" width="100">
        <template slot-scope="s">
          <el-button size="mini" type="text" @click="openEditor(s.row)">在线编辑</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog title="新建病历" :visible.sync="dialogVisible" width="520px">
      <el-form :model="form" label-width="80px" size="small">
        <el-form-item label="患者ID"><el-input v-model="form.patientId" /></el-form-item>
        <el-form-item label="患者姓名"><el-input v-model="form.patientName" /></el-form-item>
        <el-form-item label="主诉"><el-input v-model="form.chiefComplaint" /></el-form-item>
        <el-form-item label="诊断"><el-input v-model="form.diagnosis" /></el-form-item>
        <el-form-item label="治疗方案"><el-input type="textarea" v-model="form.treatment" /></el-form-item>
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
  name: 'EmrList',
  data() { return { list: [], loading: false, dialogVisible: false, form: {} } },
  created() { this.load() },
  methods: {
    async load() {
      this.loading = true
      try {
        // 演示：按患者 1 查询（实际由搜索驱动）
        const res = await request.get('/api/emr/patient/1')
        this.list = res.data || []
      } catch (e) { this.list = [] } finally { this.loading = false }
    },
    async create() {
      await request.post('/api/emr', this.form)
      this.$message.success('病历已创建')
      this.dialogVisible = false
      this.form = {}
      this.load()
    },
    openEditor(row) {
      // 调起 OnlyOffice 编辑器配置，实际场景在新窗口加载 docsapi
      request.get(`/api/emr/${row.id}/editor-config`).then(res => {
        this.$alert('OnlyOffice 编辑器配置已生成：' + JSON.stringify(res.data).slice(0, 100) + '...', '提示')
      })
    }
  }
}
</script>
