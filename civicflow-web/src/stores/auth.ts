import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '@/api/auth'
import type { CurrentUser, LoginRequest, Role, TokenPair } from '@/types/api'

export const useAuthStore = defineStore('auth', () => {
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
  function clear() { accessToken.value = null; refreshToken.value = null; user.value = null }
  async function login(payload: LoginRequest) { accept(await authApi.login(payload)) }
  async function refresh() {
    const token = refreshToken.value
    if (!token) throw new Error('登录已失效')
    // Rotation changes both tokens together; never persist either browser-side.
    accept(await authApi.refresh(token))
  }
  async function logout() {
    const token = refreshToken.value
    try { if (token && accessToken.value) await authApi.logout(token, accessToken.value) }
    finally { clear() }
  }
  return { accessToken, user, roles, authenticated, login, refresh, logout, clear }
})
