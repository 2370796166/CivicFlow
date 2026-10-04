<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { allowedPortals, firstPortal } from '@/router'
import { safeRedirect } from '@/utils/redirect'
const form = reactive({ loginName: '', password: '' })
const loading = ref(false)
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
async function submit() {
  if (!form.loginName.trim() || !form.password) return
  loading.value = true
  try {
    await auth.login({ loginName: form.loginName.trim(), password: form.password })
    const allowed = allowedPortals(auth.roles).map((item) => item.path)
    await router.replace(safeRedirect(route.query.redirect, allowed) ?? firstPortal(auth.roles))
  } catch { ElMessage.error('登录失败，请检查账号和密码。') }
  finally { form.password = ''; loading.value = false }
}
</script>
<template>
  <main class="login-screen">
    <section class="login-intro"><div class="login-wordmark"><span class="brand-mark">序</span> 智序 <small>CivicFlow</small></div><div class="login-statement"><span class="eyebrow">城市公共服务 / 统一入口</span><h1>让每一次办理，<br />都有清晰的次序。</h1><p>预约、签到、排队与窗口办理，在同一个入口衔接。</p></div><div class="login-bottom">服务有序 · 进度可见</div></section>
    <section class="login-form-wrap"><div class="login-card"><span class="eyebrow">账号登录</span><h2>欢迎使用智序</h2><p>登录后进入您有权限使用的工作空间。</p><form @submit.prevent="submit"><label for="login-name">用户名或手机号</label><el-input id="login-name" v-model="form.loginName" autocomplete="username" size="large" /><label for="password">密码</label><el-input id="password" v-model="form.password" type="password" autocomplete="current-password" show-password size="large" /><el-button native-type="submit" type="primary" size="large" :loading="loading" :disabled="!form.loginName.trim() || !form.password">登录</el-button></form><small>请勿在共享设备上保留登录页面。刷新页面后需重新登录。</small></div></section>
  </main>
</template>
