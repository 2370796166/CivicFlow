import { beforeEach, describe, expect, it, vi } from 'vitest'
import { pinia } from '../src/stores/pinia'
import { useSession } from '../src/stores/session'
import { login, logout, request, restoreSession } from '../src/api/client'
import { api } from '../src/api'
import { pending, savePending } from '../src/utils/pending'
import { clearOperation, getOperation } from '../src/utils/operations'
import type { TokenPair } from '../src/types'
const storage = new Map<string, unknown>()
const pair: TokenPair = {
  accessToken: 'old',
  refreshToken: 'r1',
  expiresIn: 3600,
  user: { id: '9223372036854775806', displayName: '测试用户', roles: ['USER'] },
}
type Options = {
  url: string
  method: string
  data?: Record<string, unknown>
  header: Record<string, string>
  success: (r: object) => void
  fail: () => void
}
let calls: Options[] = [],
  handler: (o: Options) => void
const respond = (
  o: Options,
  status: number,
  data: unknown = {},
  code = status < 300 ? 'OK' : 'ERROR',
) =>
  o.success({
    statusCode: status,
    data: { code, data, message: 'test', requestId: 'trace' },
    header: {},
  })
beforeEach(() => {
  calls = []
  storage.clear()
  vi.stubGlobal('getCurrentPages', () => [{ route: 'pages/home/index' }])
  vi.stubGlobal('uni', {
    request: (o: Options) => {
      calls.push(o)
      handler(o)
    },
    setStorageSync: (k: string, v: unknown) => storage.set(k, v),
    getStorageSync: (k: string) => storage.get(k),
    removeStorageSync: (k: string) => storage.delete(k),
    navigateTo: vi.fn(),
  })
  useSession(pinia).clear()
  useSession(pinia).accept(pair)
})
describe('session and request reliability', () => {
  it('rotates once for concurrent and delayed 401 responses', async () => {
    const queued: Options[] = []
    handler = (o) => {
      if (o.url.endsWith('/auth/refresh')) {
        queued.push(o)
        return
      }
      if (o.header.Authorization === 'Bearer old') {
        queued.push(o)
        return
      }
      respond(o, 200, { id: pair.user.id })
    }
    const a = request('/user/me'),
      b = request('/user/me'),
      c = request('/user/me')
    await Promise.resolve()
    await Promise.resolve()
    const old = queued.filter((o) => !o.url.endsWith('/auth/refresh'))
    respond(old[0]!, 401)
    respond(old[1]!, 401)
    await Promise.resolve()
    await Promise.resolve()
    const refresh = queued.find((o) => o.url.endsWith('/auth/refresh'))!
    respond(refresh, 200, { ...pair, accessToken: 'new', refreshToken: 'r2' })
    await a
    await b
    respond(old[2]!, 401)
    await c
    expect(calls.filter((o) => o.url.endsWith('/auth/refresh'))).toHaveLength(1)
    expect(useSession(pinia).refreshToken).toBe('r2')
    expect(JSON.stringify([...storage.values()])).not.toContain('accessToken')
  })
  it('restores with a persisted refresh token and rejects non-USER login', async () => {
    useSession(pinia).accessToken = ''
    useSession(pinia).refreshToken = ''
    handler = (o) => respond(o, 200, pair)
    await restoreSession()
    expect(useSession(pinia).user?.id).toBe(pair.user.id)
    handler = (o) =>
      respond(o, 200, { ...pair, user: { ...pair.user, roles: ['STAFF'] } })
    await expect(login('staff', 'secret')).rejects.toThrow('没有市民权限')
    expect(useSession(pinia).accessToken).toBe('')
  })
  it('clears failed refresh and never reinstates an in-flight session after logout', async () => {
    useSession(pinia).expiresAt = 0
    handler = (o) => respond(o, 401)
    await expect(restoreSession()).rejects.toThrow()
    expect(useSession(pinia).user).toBeNull()
    useSession(pinia).accept(pair)
    useSession(pinia).expiresAt = 0
    let refresh: Options | undefined
    handler = (o) => {
      if (o.url.endsWith('/auth/refresh')) refresh = o
      else respond(o, 200)
    }
    const restoring = restoreSession()
    await logout()
    respond(refresh!, 200, pair)
    await expect(restoring).rejects.toThrow()
    expect(useSession(pinia).user).toBeNull()
  })
  it('keeps idempotency key across authenticated write retry', async () => {
    handler = (o) =>
      o.url.endsWith('/auth/refresh')
        ? respond(o, 200, { ...pair, accessToken: 'new' })
        : o.header.Authorization === 'Bearer old'
          ? respond(o, 401)
          : respond(o, 200, {})
    await api.confirm('9223372036854775806', 7, 'fixed-key')
    const writes = calls.filter((o) => o.url.includes('/confirm'))
    expect(writes).toHaveLength(2)
    expect(writes.map((o) => o.header['Idempotency-Key'])).toEqual([
      'fixed-key',
      'fixed-key',
    ])
    expect(writes[1]!.data).toEqual({ version: 7 })
  })
  it('does not retry a network-failed reservation and recovers 503 with reservation ID', async () => {
    handler = (o) => o.fail()
    await expect(api.reserve('1', 'stable')).rejects.toThrow('网络')
    expect(calls).toHaveLength(1)
    handler = (o) =>
      respond(o, 503, {
        reservationId: 'r1',
        status: 'CREATING',
        pollAfterMs: 2500,
      })
    expect((await api.reserve('1', 'stable')).reservationId).toBe('r1')
  })
  it('preserves a pending request for recovery without generating a new key', () => {
    savePending({
      slotId: '9007199254740999',
      key: 'original',
      reservationId: 'r1',
    })
    expect(pending()?.key).toBe('original')
    expect(pending()?.slotId).toBe('9007199254740999')
    useSession(pinia).user = null
    expect(pending()).toBeNull()
    expect(storage.has('civicflow.pending.v1')).toBe(true)
    useSession(pinia).user = pair.user
    expect(pending()?.key).toBe('original')
    useSession(pinia).clear()
    expect(pending()).toBeNull()
  })
  it('retains action version/key after interruption, resets after conflict synchronization', async () => {
    const first = getOperation('9223372036854775806', 'confirm', 2)
    expect(getOperation('9223372036854775806', 'confirm', 3)).toEqual(first)
    handler = (o) => respond(o, 409, null, 'APPT_409_VERSION_CONFLICT')
    await expect(
      api.confirm('9223372036854775806', first.version, first.key),
    ).rejects.toMatchObject({ status: 409 })
    expect(calls).toHaveLength(1)
    clearOperation('9223372036854775806', 'confirm')
    const next = getOperation('9223372036854775806', 'confirm', 3)
    expect(next.key).not.toBe(first.key)
    expect(next.version).toBe(3)
    useSession(pinia).clear()
    expect(storage.has('civicflow.operations.v1')).toBe(false)
  })
  it('rejects a response received after logout instead of returning another user cache', async () => {
    let original: Options | undefined
    handler = (o) => {
      if (o.url.endsWith('/auth/logout')) respond(o, 200)
      else original = o
    }
    const reading = request('/user/me')
    await Promise.resolve()
    await Promise.resolve()
    await logout()
    useSession(pinia).accept({ ...pair, accessToken: 'new-user' })
    respond(original!, 200, pair.user)
    await expect(reading).rejects.toMatchObject({ status: 401 })
    expect(useSession(pinia).accessToken).toBe('new-user')
  })
  it('uses independent notification paths and checks server read/preference result', async () => {
    handler = (o) =>
      respond(
        o,
        200,
        o.url.endsWith('/read')
          ? { read: true }
          : o.url.endsWith('notification-preferences')
            ? { optionalInAppEnabled: false }
            : { items: [], total: 42, page: 2, size: 20 },
      )
    expect((await api.notifications(2)).page).toBe(2)
    expect((await api.read('9223372036854775806')).read).toBe(true)
    expect((await api.savePreference(false)).optionalInAppEnabled).toBe(false)
    expect(
      calls.every(
        (o) => !o.url.includes('/messages') && !o.url.includes('unread-count'),
      ),
    ).toBe(true)
    expect(calls[2]!.data).toEqual({ optionalInAppEnabled: false })
  })
})
