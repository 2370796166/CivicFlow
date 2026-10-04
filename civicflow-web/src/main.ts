import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import { pinia } from '@/stores/pinia'
import { router } from '@/router'
import { setForbiddenHandler, setUnauthorizedHandler } from '@/api/client'
import './style.css'

setUnauthorizedHandler(() => { void router.replace({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } }) })
setForbiddenHandler(() => { void router.replace({ name: 'forbidden' }) })
createApp(App).use(pinia).use(router).use(ElementPlus).mount('#app')
