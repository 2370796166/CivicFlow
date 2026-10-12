import { createApp } from 'vue'
import { ElButton, ElTag, ElInput, ElSelect, ElOption, ElPagination, ElDialog, ElTable, ElTableColumn, ElCheckbox, ElCheckboxGroup, ElLoading, ElCalendar, ElDropdown, ElDropdownItem, ElDropdownMenu, ElSkeleton } from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import { pinia } from '@/stores/pinia'
import { router } from '@/router'
import { setForbiddenHandler, setUnauthorizedHandler } from '@/api/client'
import './style.css'

setUnauthorizedHandler(() => { void router.replace({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } }) })
setForbiddenHandler(() => { void router.replace({ name: 'forbidden' }) })
const app = createApp(App).use(pinia).use(router)
for (const component of [ElButton, ElTag, ElInput, ElSelect, ElOption, ElPagination, ElDialog, ElTable, ElTableColumn, ElCheckbox, ElCheckboxGroup, ElCalendar, ElDropdown, ElDropdownItem, ElDropdownMenu, ElSkeleton]) app.use(component)
app.use(ElLoading)
app.mount('#app')
