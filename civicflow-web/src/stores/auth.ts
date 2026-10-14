import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '@/api/auth'
import type { CurrentUser, LoginRequest, Role, TokenPair } from '@/types/api'

export const useAuthStore = defineStore('auth', () => {
  let generation = 0
  const accessToken = ref<string | null>(null)
  const refreshToken = ref<string | null>(null)
  const user = ref<CurrentUser | null>(null)
  const authenticated = computed(() => Boolean(accessToken.value && user.value))
  const roles = computed<Role[]>(() => user.value?.roles ?? [])
  function accept(pair: TokenPair) {
    accessToken.value = pair.accessToken
    refreshToken.value = pair.refreshToken
    user.value = pair.user
  }
  function clear() { generation += 1; accessToken.value = null; refreshToken.value = null; user.value = null }
  async function login(payload: LoginRequest) { const request = ++generation; const pair = await authApi.login(payload); if (request === generation) accept(pair) }
  async function refresh() {
    const token = refreshToken.value
    const request = generation
    if (!token) throw new Error('登录已失效')
    // Rotation changes both tokens together; never persist either browser-side.
    const pair = await authApi.refresh(token)
    if (request !== generation || token !== refreshToken.value) throw new Error('会话已变更')
    accept(pair)
  }
  async function logout() {
    const token = refreshToken.value
    const access = accessToken.value
    clear()
    if (token && access) await authApi.logout(token, access)
  }
  return { accessToken, user, roles, authenticated, login, refresh, logout, clear, getSessionGeneration: () => generation }
})
