import { createApp } from 'vue'
import App from './App.vue'
import { pinia } from '@/stores/pinia'
import { router } from '@/router'
import { setForbiddenHandler, setUnauthorizedHandler } from '@/api/client'
import './style.css'
import './flow.css'
import './admin.css'

setUnauthorizedHandler(() => { void router.replace({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } }) })
setForbiddenHandler(() => { void router.replace({ name: 'forbidden' }) })
const app = createApp(App).use(pinia).use(router)
app.mount('#app')
