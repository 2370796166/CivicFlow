import { shallowRef } from 'vue'
import type { Credential } from '@/types'
export function createCredentialVault() {
  const value = shallowRef<Credential | null>(null)
  let generation = 0
  function clear() {
    generation++
    value.value = null
  }
  function accept(
    token: Credential,
    issuedGeneration: number,
    now = Date.now(),
  ) {
    if (issuedGeneration !== generation || Date.parse(token.expiresAt) <= now)
      return false
    value.value = token
    return true
  }
  function expire(now = Date.now()) {
    if (value.value && Date.parse(value.value.expiresAt) <= now) {
      clear()
      return true
    }
    return false
  }
  return { value, clear, accept, expire, generation: () => generation }
}
