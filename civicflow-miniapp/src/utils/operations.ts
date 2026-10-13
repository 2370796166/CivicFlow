import { pinia } from '@/stores/pinia'
import { useSession } from '@/stores/session'
import { operationKey } from './flow'
const STORAGE = 'civicflow.operations.v1'
interface Operation {
  key: string
  version: number
  owner: string
  savedAt: number
}
function read(): Record<string, Operation> {
  return uni.getStorageSync(STORAGE) || {}
}
export function getOperation(id: string, action: string, version: number) {
  const values = read(),
    index = id + ':' + action,
    owner = useSession(pinia).user?.id || ''
  const old = values[index]
  if (old && old.owner === owner && Date.now() - old.savedAt < 86400000)
    return old
  const op = { key: operationKey(), version, owner, savedAt: Date.now() }
  values[index] = op
  uni.setStorageSync(STORAGE, values)
  return op
}
export function clearOperation(id: string, action: string) {
  const values = read()
  delete values[id + ':' + action]
  uni.setStorageSync(STORAGE, values)
}
