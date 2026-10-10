import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import './styles/global.css'
import App from './App.vue'
import router from './router'
import { permission } from './directives/permission'

const app = createApp(App)
// 全局注册所有 Element Plus 图标，页面内 <el-icon><Edit /></el-icon> 无需逐个 import
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}
app.use(createPinia())
app.use(router)
// 全局中文语言包：确认框按钮、分页、空数据、日期选择等内置文案统一为中文
app.use(ElementPlus, { locale: zhCn })
app.directive('permission', permission)
app.mount('#app')
