<template>
  <div class="pconfig" v-loading="loading">
    <el-row :gutter="isMobile ? 0 : 16">
      <!-- 左：配置编辑 -->
      <el-col :span="isMobile ? 24 : 14">
        <div class="panel">
          <div class="panel-head">
            <span class="t">Portal 配置</span>
            <el-button type="primary" :icon="Check" @click="save">保存</el-button>
          </div>
          <el-table :data="rows" border size="default">
            <el-table-column prop="configKey" label="配置项" width="180">
              <template #default="{ row }">
                <span class="k">{{ row.configKey }}</span>
                <span class="kl">{{ labelOf(row.configKey) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="configValue" label="配置值">
              <template #default="{ row }">
                <el-switch v-if="isBool(row.configKey)"
                  v-model="row.configValue" active-value="true" inactive-value="false" />
                <el-input v-else v-model="row.configValue" />
              </template>
            </el-table-column>
            <el-table-column label="说明" min-width="160">
              <template #default="{ row }">{{ row.remark }}</template>
            </el-table-column>
          </el-table>
          <p class="hint">改完点右上角「保存」，右侧预览即时反映当前值。</p>
        </div>
      </el-col>

      <!-- 右：实时预览 -->
      <el-col :span="isMobile ? 24 : 10">
        <div class="panel">
          <div class="panel-head"><span class="t">预览（终端用户所见）</span></div>
          <div class="preview">
            <div class="pv-card">
              <div class="pv-title">{{ val('title') || '网络准入认证' }}</div>
              <div v-if="val('notice')" class="pv-notice">{{ val('notice') }}</div>
              <div class="pv-field">手机号 <span class="pv-ph">请输入手机号</span></div>
              <div class="pv-field">短信验证码 <span class="pv-ph">验证码</span> <el-button size="small" disabled>获取</el-button></div>
              <el-button type="primary" style="width:100%" disabled>认证入网</el-button>
              <el-divider>生效策略</el-divider>
              <div class="pv-tags">
                <el-tag size="small" :type="on('mabEnabled') ? 'success' : 'info'">MAB 自动认证 {{ on('mabEnabled') ? '开' : '关' }}</el-tag>
                <el-tag size="small" :type="on('phoneAuditEnabled') ? 'warning' : 'info'">手机号审核 {{ on('phoneAuditEnabled') ? '开' : '关' }}</el-tag>
                <el-tag size="small" type="primary">访客 VLAN {{ val('visitorVlan') || '-' }}</el-tag>
                <el-tag size="small" type="primary">授权 {{ val('grantMinutes') || '-' }} 分钟</el-tag>
              </div>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Check } from '@element-plus/icons-vue'
import { portalAdminConfig, updatePortalConfig } from '../api/auth'
import { useBreakpoints } from '../composables/useBreakpoints'
const { isMobile } = useBreakpoints()

const LABELS = {
  title: '页面标题', notice: '公告', mabEnabled: 'MAB 自动认证',
  visitorVlan: '访客 VLAN', grantMinutes: '授权时长(分钟)', phoneAuditEnabled: '手机号审核'
}
const BOOLS = ['mabEnabled', 'phoneAuditEnabled']

const loading = ref(false)
const rows = ref([])
const map = computed(() => Object.fromEntries(rows.value.map(r => [r.configKey, r.configValue])))
const val = (k) => map.value[k] ?? ''
const on = (k) => String(map.value[k]) === 'true'
const labelOf = (k) => LABELS[k] || ''
const isBool = (k) => BOOLS.includes(k)

async function save() {
  loading.value = true
  try {
    const kv = {}
    rows.value.forEach(r => (kv[r.configKey] = r.configValue))
    await updatePortalConfig(kv)
    ElMessage.success('已保存')
  } finally { loading.value = false }
}
onMounted(async () => {
  loading.value = true
  try {
    const r = await portalAdminConfig()
    rows.value = r.data || []
  } finally { loading.value = false }
})
</script>

<style scoped>
.pconfig .panel { background: #fff; border-radius: 8px; padding: 16px; margin-bottom: 12px; }
.panel-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.panel-head .t { font-weight: 600; font-size: 15px; }
.k { font-family: monospace; font-size: 13px; }
.kl { color: #909399; font-size: 12px; margin-left: 6px; }
.hint { color: #909399; font-size: 12px; margin: 10px 2px 0; }
.preview { background: #f0f2f5; border-radius: 8px; padding: 24px; display: flex; justify-content: center; }
.pv-card { width: 320px; background: #fff; border-radius: 8px; padding: 18px; box-shadow: 0 2px 12px rgba(0,0,0,.08); }
.pv-title { font-size: 17px; font-weight: 600; text-align: center; margin-bottom: 10px; }
.pv-notice { background: #ecf5ff; color: #409eff; border-radius: 4px; padding: 6px 10px; font-size: 12px; margin-bottom: 12px; }
.pv-field { display: flex; align-items: center; gap: 8px; font-size: 13px; color: #606266; margin-bottom: 12px; }
.pv-ph { flex: 1; border: 1px solid #dcdfe6; border-radius: 4px; padding: 6px 10px; color: #c0c4cc; }
.pv-tags { display: flex; flex-wrap: wrap; gap: 8px; justify-content: center; }
</style>
