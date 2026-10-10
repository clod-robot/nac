<template>
  <div class="pcustom" v-loading="loading">
    <div class="pc-head">
      <span class="pc-title"><el-icon><Picture /></el-icon> Portal 定制页面</span>
      <span class="pc-sub">左侧为终端用户所见的实时预览，右侧修改后即时生效</span>
    </div>

    <el-row :gutter="isMobile ? 0 : 16" class="pc-body">
      <!-- 左：实时预览（仿迈普 Portal 页） -->
      <el-col :span="isMobile ? 24 : 10">
        <div class="panel">
          <div class="panel-head"><span class="t">预览（终端用户所见）</span></div>
          <div class="pv-stage">
            <div class="pv-phone" :style="phoneBgStyle">
              <div class="pv-topbar">
                <img v-if="form.logo" :src="form.logo" class="pv-logo" />
                <span v-else class="pv-logo-ph">LOGO</span>
                <span class="pv-site">{{ form.siteName || '默认站点' }}</span>
              </div>
              <div class="pv-banner">
                <div class="pv-banner-title">{{ pvTitleLines[0] }}</div>
                <div v-if="pvTitleLines[1]" class="pv-banner-sub">{{ pvTitleLines[1] }}</div>
              </div>
              <div v-if="form.notice" class="pv-notice">{{ form.notice }}</div>
              <div v-if="carouselList.length" class="pv-dots">
                <span v-for="(d, i) in carouselList" :key="i" class="pv-dot" :class="{ on: i === 0 }"></span>
              </div>
              <div v-if="pvType === 'both'" class="pv-tabs">
                <span class="pv-tab" :class="{ on: pvActive === 'sms' }" @click="pvActive = 'sms'">短信认证</span>
                <span class="pv-tab" :class="{ on: pvActive === 'account' }" @click="pvActive = 'account'">账号认证</span>
              </div>
              <template v-if="pvShowSms">
                <div class="pv-field">
                  <el-icon><User /></el-icon><span class="pv-ph">请输入手机号</span>
                </div>
                <div class="pv-field pv-field-cap">
                  <span class="pv-ph">图形验证码</span>
                  <span class="pv-capimg">验证码</span>
                </div>
                <div class="pv-field pv-field-cap">
                  <span class="pv-ph">短信验证码</span>
                  <span class="pv-getbtn">获取</span>
                </div>
              </template>
              <template v-if="pvShowAccount">
                <div class="pv-field">
                  <el-icon><User /></el-icon><span class="pv-ph">请输入账号</span>
                </div>
                <div class="pv-field">
                  <el-icon><Lock /></el-icon><span class="pv-ph">请输入密码</span>
                </div>
              </template>
              <div class="pv-btn">{{ form.buttonText || '登录' }}</div>
              <div v-if="form.copyright" class="pv-copy">{{ form.copyright }}</div>
            </div>
          </div>
        </div>
      </el-col>

      <!-- 右：配置表单 -->
      <el-col :span="isMobile ? 24 : 14">
        <div class="panel">
          <el-form :model="form" label-width="110px" label-position="right" size="default">
            <el-form-item label="站点名称" required>
              <el-input v-model="form.siteName" maxlength="32" show-word-limit placeholder="默认站点" />
            </el-form-item>
            <el-form-item label="LOGO">
              <div class="up-row">
                <el-upload :show-file-list="false" accept="image/png,image/jpeg"
                  :before-upload="(f) => checkSize(f, 200)" :http-request="(o) => uploadOne(o, 'logo')">
                  <el-button size="small" type="primary" :icon="Upload">上传</el-button>
                </el-upload>
                <span v-if="logoName" class="fname" :title="logoName">{{ logoName }}</span>
                <el-button v-if="form.logo" link type="danger" size="small" @click="form.logo = ''; logoName = ''">移除</el-button>
              </div>
              <div class="ftip">图片格式为 png 或 jpg，不能超过 200KB</div>
            </el-form-item>
            <el-form-item label="标题">
              <el-input v-model="form.title" maxlength="32" show-word-limit placeholder="请输入标题" />
            </el-form-item>
            <el-form-item label="公告栏">
              <el-input v-model="form.notice" maxlength="128" show-word-limit placeholder="请输入公告" />
            </el-form-item>
            <el-form-item label="轮播图片">
              <div class="up-row">
                <el-upload :show-file-list="false" accept="image/png,image/jpeg"
                  :before-upload="(f) => checkSize(f, 1024)" :http-request="(o) => uploadOne(o, 'carousel')">
                  <el-button size="small" type="primary" :icon="Upload">上传</el-button>
                </el-upload>
                <span v-for="(u, i) in carouselList" :key="i" class="tag-pill">
                  图片{{ i + 1 }}
                  <el-icon class="rm" @click="removeCarousel(i)"><Close /></el-icon>
                </span>
              </div>
              <div class="ftip">图片格式为 png 或 jpg，不能超过 1MB</div>
            </el-form-item>
            <el-form-item label="背景">
              <el-radio-group v-model="form.bgType">
                <el-radio label="image">图片</el-radio>
                <el-radio label="color">纯色</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item v-if="form.bgType === 'image'" label="背景图片">
              <div class="up-row">
                <el-upload :show-file-list="false" accept="image/png,image/jpeg"
                  :before-upload="(f) => checkSize(f, 1024)" :http-request="(o) => uploadOne(o, 'bg')">
                  <el-button size="small" type="primary" :icon="Upload">上传</el-button>
                </el-upload>
                <span v-if="bgName" class="fname" :title="bgName">{{ bgName }}</span>
                <el-button v-if="form.bgImage" link type="danger" size="small" @click="form.bgImage = ''; bgName = ''">移除</el-button>
              </div>
              <div class="ftip">图片格式为 png 或 jpg，不能超过 1MB</div>
            </el-form-item>
            <el-form-item v-if="form.bgType === 'color'" label="背景纯色">
              <el-color-picker v-model="form.bgColor" />
              <span class="fname">{{ form.bgColor }}</span>
            </el-form-item>
            <el-form-item label="版权">
              <el-input v-model="form.copyright" type="textarea" :rows="2" maxlength="100" show-word-limit placeholder="Copyright © ..." />
            </el-form-item>
            <el-form-item label="免认证认证">
              <el-radio-group v-model="form.anonymousAuth">
                <el-radio label="false">关闭</el-radio>
                <el-radio label="true">开启</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="认证类型" required>
              <el-radio-group v-model="form.portalAuthType">
                <el-radio label="sms">短信认证</el-radio>
                <el-radio label="account">账号认证</el-radio>
                <el-radio label="both">两者都支持</el-radio>
              </el-radio-group>
              <div class="ftip">账号认证使用系统中已开通的网络账号（账号 + 密码）</div>
            </el-form-item>
            <el-form-item label="认证按钮文字" required>
              <el-input v-model="form.buttonText" maxlength="16" placeholder="登录" />
            </el-form-item>
          </el-form>
        </div>
        <div class="pc-foot">
          <el-button @click="restoreDefault">恢复默认</el-button>
          <el-button @click="cancel">取消</el-button>
          <el-button type="primary" :loading="saving" @click="confirm">确定</el-button>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Picture, Upload, User, Close, Lock } from '@element-plus/icons-vue'
import { portalAdminConfig, updatePortalConfig, uploadPortalAsset } from '../api/auth'
import { useBreakpoints } from '../composables/useBreakpoints'
const { isMobile } = useBreakpoints()

const DEFAULTS = {
  siteName: '默认站点', logo: '', title: '网络准入认证', notice: '欢迎',
  carousel: '', bgType: 'color', bgImage: '', bgColor: '#0b5cab',
  copyright: '', anonymousAuth: 'false', portalAuthType: 'sms', buttonText: '登录'
}
const KEYS = Object.keys(DEFAULTS)

const loading = ref(false)
const saving = ref(false)
const form = reactive({ ...DEFAULTS })
const saved = ref({ ...DEFAULTS })   // 取消时回滚的快照
const logoName = ref('')
const bgName = ref('')
const pvActive = ref('sms')
const pvType = computed(() => form.portalAuthType || 'sms')
const pvShowSms = computed(() => pvType.value === 'sms' || (pvType.value === 'both' && pvActive.value === 'sms'))
const pvShowAccount = computed(() => pvType.value === 'account' || (pvType.value === 'both' && pvActive.value === 'account'))

const carouselList = computed(() => (form.carousel || '').split(',').map(s => s.trim()).filter(Boolean))
const pvTitleLines = computed(() => {
  const t = form.title || '网络准入认证'
  const mid = Math.ceil(t.length / 2)
  return t.length > 8 ? [t.slice(0, mid), t.slice(mid)] : [t]
})
const phoneBgStyle = computed(() => {
  if (form.bgType === 'image' && form.bgImage) {
    return { backgroundImage: `url(${form.bgImage})`, backgroundSize: 'cover', backgroundPosition: 'center' }
  }
  return { background: form.bgColor || '#0b5cab' }
})

function checkSize(file, maxKB) {
  if (file.size > maxKB * 1024) { ElMessage.error(`图片不能超过 ${maxKB}KB`); return false }
  return true
}
async function uploadOne(option, target) {
  const fd = new FormData()
  fd.append('file', option.file)
  try {
    const r = await uploadPortalAsset(fd)
    const url = r.data
    if (target === 'logo') { form.logo = url; logoName.value = option.file.name }
    else if (target === 'bg') { form.bgImage = url; bgName.value = option.file.name }
    else if (target === 'carousel') {
      const arr = (form.carousel || '').split(',').map(s => s.trim()).filter(Boolean)
      arr.push(url)
      form.carousel = arr.join(',')
    }
    ElMessage.success('上传成功')
  } catch (e) {
    ElMessage.error('上传失败：' + (e?.response?.data?.message || e.message))
  }
}
function removeCarousel(i) {
  const arr = carouselList.value.slice()
  arr.splice(i, 1)
  form.carousel = arr.join(',')
}

function restoreDefault() {
  Object.assign(form, DEFAULTS)
  logoName.value = ''
  bgName.value = ''
  ElMessage.info('已恢复默认（点击「确定」后生效）')
}
function cancel() {
  Object.assign(form, saved.value)
  ElMessage.info('已取消修改')
}
async function confirm() {
  if (!form.siteName.trim()) { ElMessage.warning('请填写站点名称'); return }
  if (!form.buttonText.trim()) { ElMessage.warning('请填写认证按钮文字'); return }
  saving.value = true
  try {
    const kv = {}
    KEYS.forEach(k => (kv[k] = String(form[k] ?? '')))
    await updatePortalConfig(kv)
    Object.assign(saved.value, form)
    ElMessage.success('定制已保存并生效')
  } catch (e) {
    ElMessage.error('保存失败：' + (e?.response?.data?.message || e.message))
  } finally { saving.value = false }
}

onMounted(async () => {
  loading.value = true
  try {
    const r = await portalAdminConfig()
    const map = {}
    ;(r.data || []).forEach(x => (map[x.configKey] = x.configValue))
    KEYS.forEach(k => { if (map[k] !== undefined) form[k] = map[k] })
    Object.assign(saved.value, form)
  } finally { loading.value = false }
})
</script>

<style scoped>
.pcustom { padding-bottom: 8px; }
.pc-head { display: flex; align-items: baseline; gap: 10px; margin-bottom: 12px; }
.pc-title { font-size: 16px; font-weight: 600; color: #303133; display: inline-flex; align-items: center; gap: 6px; }
.pc-sub { font-size: 12px; color: #909399; }
.panel { background: #fff; border-radius: 8px; padding: 16px; margin-bottom: 12px; }
.panel-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.panel-head .t { font-weight: 600; font-size: 15px; }
.pc-foot { display: flex; justify-content: flex-end; gap: 10px; }
.up-row { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
.fname { font-size: 13px; color: #606266; font-family: monospace; }
.ftip { font-size: 12px; color: #909399; margin-top: 4px; }
.tag-pill { display: inline-flex; align-items: center; gap: 4px; background: #f0f2f5; border: 1px solid #e4e7ed; border-radius: 12px; padding: 2px 8px; font-size: 12px; color: #606266; }
.tag-pill .rm { cursor: pointer; color: #f56c6c; margin-left: 2px; }
/* 预览舞台 */
.pv-stage { background: #f0f2f5; border-radius: 8px; padding: 20px; display: flex; justify-content: center; }
.pv-phone { width: 300px; min-height: 460px; border-radius: 10px; overflow: hidden; box-shadow: 0 4px 16px rgba(0,0,0,.2); color: #fff; position: relative; display: flex; flex-direction: column; }
.pv-phone::after { content: ''; position: absolute; inset: 0; background: rgba(0,0,0,.05); pointer-events: none; }
.pv-topbar { display: flex; align-items: center; justify-content: space-between; padding: 10px 12px; background: rgba(0,0,0,.18); }
.pv-logo { height: 22px; max-width: 120px; object-fit: contain; }
.pv-logo-ph { font-size: 12px; opacity: .7; border: 1px dashed rgba(255,255,255,.6); padding: 1px 8px; border-radius: 3px; }
.pv-site { font-size: 13px; font-weight: 600; }
.pv-banner { padding: 32px 18px; text-align: center; }
.pv-banner-title { font-size: 19px; font-weight: 700; text-shadow: 0 1px 4px rgba(0,0,0,.35); }
.pv-banner-sub { font-size: 19px; font-weight: 700; margin-top: 8px; text-shadow: 0 1px 4px rgba(0,0,0,.35); }
.pv-notice { margin: 0 14px; background: rgba(255,255,255,.9); color: #303133; border-radius: 4px; padding: 6px 10px; font-size: 12px; }
.pv-dots { display: flex; justify-content: center; gap: 6px; margin: 12px 0 4px; }
.pv-dot { width: 6px; height: 6px; border-radius: 50%; background: rgba(255,255,255,.45); }
.pv-dot.on { background: #fff; }
.pv-tabs { display: flex; gap: 6px; margin: 10px 14px 0; }
.pv-tab { flex: 1; text-align: center; font-size: 12px; padding: 5px 0; border-radius: 4px; background: rgba(255,255,255,.22); cursor: pointer; }
.pv-tab.on { background: #fff; color: #333; font-weight: 600; }
.pv-field { display: flex; align-items: center; gap: 8px; margin: 10px 14px 0; background: #fff; border-radius: 4px; padding: 8px 10px; color: #c0c4cc; }
.pv-field .el-icon { color: #909399; }
.pv-field-cap { justify-content: space-between; }
.pv-capimg { font-size: 11px; color: #409eff; border: 1px dashed #a0cfff; border-radius: 3px; padding: 1px 8px; }
.pv-getbtn { font-size: 12px; color: #409eff; white-space: nowrap; }
.pv-btn { margin: 16px 14px; background: #fff; color: #333; border-radius: 4px; text-align: center; padding: 9px 0; font-weight: 600; }
.pv-copy { margin: auto 14px 10px; text-align: center; font-size: 10px; opacity: .8; line-height: 1.4; }
</style>
