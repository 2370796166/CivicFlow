import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { TokenPair, User } from '@/types'
import { REFRESH_MAX_AGE } from '@/config'
const STORAGE = 'civicflow.refresh.v1'
const clearHandlers = new Set<() => void>()
export function onSessionClear(fn: () => void) {
  clearHandlers.add(fn)
  return () => {
    clearHandlers.delete(fn)
  }
}
export const useSession = defineStore('session', () => {
  const accessToken = ref(''),
    refreshToken = ref(''),
    user = ref<User | null>(null),
    expiresAt = ref(0),
    epoch = ref(0)
  function clear() {
    epoch.value++
    accessToken.value = ''
    refreshToken.value = ''
    user.value = null
    expiresAt.value = 0
    uni.removeStorageSync(STORAGE)
    uni.removeStorageSync('civicflow.pending.v1')
    uni.removeStorageSync('civicflow.operations.v1')
    clearHandlers.forEach((fn) => fn())
  }
  function accept(pair: TokenPair) {
    if (!pair.user.roles.includes('USER')) {
      clear()
      throw new Error('此账号没有市民权限，请使用具有 USER 权限的账号。')
    }
    accessToken.value = pair.accessToken
    refreshToken.value = pair.refreshToken
    user.value = pair.user
    expiresAt.value = Date.now() + pair.expiresIn * 1000
    if (REFRESH_MAX_AGE > 0)
      uni.setStorageSync(STORAGE, {
        token: pair.refreshToken,
        expiresAt: Date.now() + REFRESH_MAX_AGE,
      })
    else uni.removeStorageSync(STORAGE)
  }
  function storedRefresh(): string {
    const saved = uni.getStorageSync(STORAGE)
    if (
      saved &&
      typeof saved.token === 'string' &&
      saved.expiresAt > Date.now()
    )
      return saved.token
    uni.removeStorageSync(STORAGE)
    return ''
  }
  return {
    accessToken,
    refreshToken,
    user,
    expiresAt,
    epoch,
    clear,
    accept,
    storedRefresh,
  }
})
