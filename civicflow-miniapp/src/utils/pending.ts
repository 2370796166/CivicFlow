import { useSession } from '@/stores/session'
import { pinia } from '@/stores/pinia'
export interface Pending {
  owner: string
  slotId: string
  key: string
  reservationId?: string
  pollAfterMs?: number
  savedAt: number
}
const KEY = 'civicflow.pending.v1'
export function pending(): Pending | null {
  // App launch restores the session asynchronously. Do not discard another
  // request before the persisted refresh token has established its owner.
  if (!useSession(pinia).user) return null
  const saved = uni.getStorageSync(KEY) as Pending | undefined
  if (
    saved &&
    saved.owner === useSession(pinia).user?.id &&
    Date.now() - saved.savedAt < 86400000
  )
    return saved
  uni.removeStorageSync(KEY)
  return null
}
export function savePending(value: Omit<Pending, 'owner' | 'savedAt'>) {
  uni.setStorageSync(KEY, {
    ...value,
    owner: useSession(pinia).user?.id,
    savedAt: Date.now(),
  })
}
export function clearPending() {
  uni.removeStorageSync(KEY)
}
