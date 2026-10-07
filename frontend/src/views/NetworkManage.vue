<template>
  <div>
    <el-card v-loading="loading">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span>网络管理</span>
          <el-button size="small" @click="load">刷新</el-button>
        </div>
      </template>
      <el-alert type="warning" :closable="false" show-icon style="margin-bottom:14px"
        title="修改地址/网关可能导致服务器断连，请确认操作。修改即时生效，重启后由系统网络服务还原。" />

      <div v-for="n in list" :key="n.name" style="margin-bottom:16px">
        <el-card shadow="never">
          <template #header>
            <div style="display:flex;align-items:center;gap:10px">
              <b>{{ n.name }}</b>
              <el-tag :type="n.up ? 'success' : 'info'" size="small">{{ n.up ? 'UP' : 'DOWN' }}</el-tag>
              <el-tag size="small" type="info">速率 {{ n.speedMbps ? n.speedMbps + ' Mbps' : '未知' }}</el-tag>
              <el-tag size="small" type="info">MTU {{ n.mtu }}</el-tag>
              <el-tag size="small" type="info">{{ n.operState }}</el-tag>
              <div style="margin-left:auto;display:flex;gap:8px">
                <el-button size="small" type="primary" @click="openEdit(n)">修改</el-button>
                <el-button size="small" :type="n.up ? 'danger' : 'success'" @click="toggleUp(n)">
                  {{ n.up ? '禁用' : '启用' }}
                </el-button>
              </div>
            </div>
          </template>

          <el-table :data="[n]" size="small" border style="margin-bottom:10px">
            <el-table-column label="IPv4 地址" prop="ipv4.address" width="150">
              <template #default="{ row }">{{ row.ipv4?.address || '-' }}</template>
            </el-table-column>
            <el-table-column label="掩码" width="140">
              <template #default="{ row }">{{ row.ipv4?.mask || '-' }}（/{{ row.ipv4?.prefix }}）</template>
            </el-table-column>
            <el-table-column label="网关">{{ n.gateway || '-' }}</el-table-column>
            <el-table-column label="主 DNS">{{ n.dns1 || '-' }}</el-table-column>
            <el-table-column label="备 DNS">{{ n.dns2 || '-' }}</el-table-column>
          </el-table>

          <el-table :data="[n]" size="small" border>
            <el-table-column label="IPv6 地址" prop="ipv6.address" width="240">
              <template #default="{ row }">{{ row.ipv6?.address || '-' }}</template>
            </el-table-column>
            <el-table-column label="前缀" width="80">
              <template #default="{ row }">{{ row.ipv6?.prefix ?? '-' }}</template>
            </el-table-column>
            <el-table-column label="网关">{{ n.gateway || '-' }}</el-table-column>
            <el-table-column label="主 DNS">{{ n.dns1 || '-' }}</el-table-column>
            <el-table-column label="备 DNS">{{ n.dns2 || '-' }}</el-table-column>
            <el-table-column label="MTU" width="80">{{ n.mtu }}</el-table-column>
          </el-table>
        </el-card>
      </div>
      <el-empty v-if="!loading && list.length === 0" description="未发现可用网络接口" />
    </el-card>

    <el-dialog v-model="dlg" title="修改网络配置" width="560px" @close="saving = false">
      <el-form label-width="110px">
        <el-form-item label="启用">
          <el-switch v-model="form.up" />
        </el-form-item>
        <el-form-item label="MTU"><el-input-number v-model="form.mtu" :min="576" :max="9000" /></el-form-item>
        <el-form-item label="IPv4 地址"><el-input v-model="form.ipv4Address" placeholder="如 192.168.10.88" /></el-form-item>
        <el-form-item label="IPv4 前缀"><el-input-number v-model="form.ipv4Prefix" :min="0" :max="32" /></el-form-item>
        <el-form-item label="IPv6 地址"><el-input v-model="form.ipv6Address" placeholder="留空则不修改" /></el-form-item>
        <el-form-item label="IPv6 前缀"><el-input-number v-model="form.ipv6Prefix" :min="0" :max="128" /></el-form-item>
        <el-form-item label="默认网关"><el-input v-model="form.gateway" placeholder="留空则不修改" /></el-form-item>
        <el-form-item label="主 DNS"><el-input v-model="form.dns1" placeholder="留空则不修改" /></el-form-item>
        <el-form-item label="备 DNS"><el-input v-model="form.dns2" placeholder="留空则不修改" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doApply">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getNetworkInterfaces, applyNetwork } from '../api/auth'

const loading = ref(true)
const list = ref([])
const dlg = ref(false)
const saving = ref(false)
const form = reactive({})

onMounted(load)
async function load() {
  loading.value = true
  try { list.value = (await getNetworkInterfaces()).data || [] } finally { loading.value = false }
}
function openEdit(n) {
  Object.assign(form, {
    name: n.name, up: n.up, mtu: n.mtu,
    ipv4Address: n.ipv4?.address || '', ipv4Prefix: n.ipv4?.prefix ?? null,
    ipv6Address: n.ipv6?.address || '', ipv6Prefix: n.ipv6?.prefix ?? null,
    gateway: n.gateway || '', dns1: n.dns1 || '', dns2: n.dns2 || ''
  })
  dlg.value = true
}
async function toggleUp(n) {
  const to = !n.up
  await ElMessageBox.confirm(`确定${to ? '启用' : '禁用'}接口 ${n.name} 吗？`, '提示', { type: 'warning' })
  await applyNetwork({ name: n.name, up: to })
  ElMessage.success('已' + (to ? '启用' : '禁用'))
  load()
}
async function doApply() {
  saving.value = true
  try {
    await applyNetwork(JSON.parse(JSON.stringify(form)))
    ElMessage.success('保存成功')
    dlg.value = false; load()
  } finally { saving.value = false }
}
</script>
