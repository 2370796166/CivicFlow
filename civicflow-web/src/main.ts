import { createApp } from 'vue'
import { ElButton, ElInput, ElSelect, ElOption, ElTable, ElTableColumn, ElPagination, ElDialog, ElCheckbox, ElCheckboxGroup, ElCalendar, ElDropdown, ElDropdownItem, ElDropdownMenu, ElSkeleton, ElTag } from 'element-plus'
import 'element-plus/es/components/button/style/css.mjs'
import 'element-plus/es/components/input/style/css.mjs'
import 'element-plus/es/components/select/style/css.mjs'
import 'element-plus/es/components/table/style/css.mjs'
import 'element-plus/es/components/pagination/style/css.mjs'
import 'element-plus/es/components/dialog/style/css.mjs'
import 'element-plus/es/components/checkbox/style/css.mjs'
import 'element-plus/es/components/calendar/style/css.mjs'
import 'element-plus/es/components/dropdown/style/css.mjs'
import 'element-plus/es/components/skeleton/style/css.mjs'
import 'element-plus/es/components/tag/style/css.mjs'
import 'element-plus/es/components/message/style/css.mjs'
import 'element-plus/es/components/message-box/style/css.mjs'
import App from './App.vue'
import { pinia } from '@/stores/pinia'
import { router } from '@/router'
import { setForbiddenHandler, setUnauthorizedHandler } from '@/api/client'
import './style.css'
import './flow.css'

setUnauthorizedHandler(() => { void router.replace({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } }) })
setForbiddenHandler(() => { void router.replace({ name: 'forbidden' }) })
const app = createApp(App).use(pinia).use(router)
const components = { ElButton, ElInput, ElSelect, ElOption, ElTable, ElTableColumn, ElPagination, ElDialog, ElCheckbox, ElCheckboxGroup, ElCalendar, ElDropdown, ElDropdownItem, ElDropdownMenu, ElSkeleton, ElTag }
for (const [name, component] of Object.entries(components)) app.component(name, component)
app.mount('#app')
