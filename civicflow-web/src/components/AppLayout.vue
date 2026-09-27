<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { allowedPortals } from '@/router'
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const menu = computed(() => allowedPortals(auth.roles))
const current = computed(() => menu.value.find((item) => route.path.startsWith(item.path)))
const busy = ref(false)
async function signOut() {
  busy.value = true
  try { await auth.logout() } catch { /* Local session is cleared even if server is unavailable. */ }
  finally { busy.value = false; await router.replace('/login') }
}
</script>

<template>
  <div class="shell">
    <aside
      class="sidebar"
      aria-label="主导航"
    >
      <RouterLink
        class="brand"
        to="/"
      >
        <span class="brand-mark">序</span><span>智序 <small>CivicFlow</small></span>
      </RouterLink>
      <div class="sidebar-caption">
        工作空间
      </div>
      <nav class="side-nav">
        <template v-if="auth.roles.includes('USER')">
          <RouterLink
            to="/user"
            :class="{ active: route.path === '/user' || route.path.startsWith('/user/outlets') }"
          >
            <span class="nav-dot" />服务网点
          </RouterLink><RouterLink
            to="/user/appointments"
            :class="{ active: route.path.startsWith('/user/appointments') }"
          >
            <span class="nav-dot" />我的预约
          </RouterLink>
        </template>
        <template v-if="auth.roles.includes('ADMIN')">
          <RouterLink
            v-for="entry in [{ path: '/admin/outlets', title: '网点' }, { path: '/admin/items', title: '事项' }, { path: '/admin/windows', title: '窗口' }, { path: '/admin/slots', title: '号源日历' }, { path: '/admin/users', title: '用户与角色' }, { path: '/admin/reconciliations', title: '号源对账' }, { path: '/admin/appointments', title: '预约查询' }, { path: '/admin/operation-logs', title: '操作日志' }]"
            :key="entry.path"
            :to="entry.path"
            :class="{ active: route.path === entry.path }"
          >
            <span class="nav-dot" />{{ entry.title }}
          </RouterLink>
        </template>
        <RouterLink
          v-for="item in menu.filter((entry) => entry.role === 'STAFF')"
          :key="item.role"
          :to="item.path"
          :class="{ active: route.path.startsWith(item.path) }"
        >
          <span class="nav-dot" />{{ item.title }}
        </RouterLink>
      </nav>
      <p class="sidebar-note">
        公共服务预约与排队平台
      </p>
    </aside>
    <div class="main-column">
      <header class="topbar">
        <div><span class="eyebrow">{{ current?.role }} / 工作空间</span><strong>{{ current?.title }}</strong></div>
        <div class="account">
          <span>{{ auth.user?.displayName }}</span><el-button
            text
            :loading="busy"
            @click="signOut"
          >
            退出登录
          </el-button>
        </div>
      </header>
      <main class="main-content">
        <RouterView />
      </main>
    </div>
  </div>
</template>
