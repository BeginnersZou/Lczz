import { createApp } from 'vue'
import { createPinia } from 'pinia'
import {
  ElAlert, ElAside, ElBadge, ElBreadcrumb, ElBreadcrumbItem, ElButton, ElCard,
  ElCascader, ElCheckbox, ElContainer, ElDatePicker, ElDescriptions,
  ElDescriptionsItem, ElDialog, ElDropdown, ElDropdownItem, ElDropdownMenu,
  ElEmpty, ElForm, ElFormItem, ElHeader, ElIcon, ElImage, ElInput,
  ElInputNumber, ElMain, ElMenu, ElMenuItem, ElOption, ElPagination,
  ElRadioButton, ElRadioGroup, ElSelect, ElSubMenu, ElTable, ElTableColumn,
  ElTag, ElUpload, ElLoading
} from 'element-plus'
import 'element-plus/dist/index.css'
import './style/theme.scss'
import App from './App.vue'
import router from './router/index'

// 1. 先引入路由守卫（必须在use(router)前）
import './router/guard'

const app = createApp(App)
const pinia = createPinia()

const elementComponents = [
  ElAlert, ElAside, ElBadge, ElBreadcrumb, ElBreadcrumbItem, ElButton, ElCard,
  ElCascader, ElCheckbox, ElContainer, ElDatePicker, ElDescriptions,
  ElDescriptionsItem, ElDialog, ElDropdown, ElDropdownItem, ElDropdownMenu,
  ElEmpty, ElForm, ElFormItem, ElHeader, ElIcon, ElImage, ElInput,
  ElInputNumber, ElMain, ElMenu, ElMenuItem, ElOption, ElPagination,
  ElRadioButton, ElRadioGroup, ElSelect, ElSubMenu, ElTable, ElTableColumn,
  ElTag, ElUpload
]

elementComponents.forEach(component => app.component(component.name, component))

// 按顺序挂载插件
app.use(pinia)
app.use(router)
app.use(ElLoading)

app.mount('#app')
