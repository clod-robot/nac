import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
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
app.use(ElementPlus)
app.directive('permission', permission)
app.mount('#app')
