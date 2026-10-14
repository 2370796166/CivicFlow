import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from './auth'
import type { TokenPair } from '@/types/api'

const api = vi.hoisted(() => ({ login: vi.fn(), refresh: vi.fn(), logout: vi.fn() }))
vi.mock('@/api/auth', () => ({ authApi: api }))
const pair = { accessToken: 'access-1', refreshToken: 'refresh-1', expiresIn: 900, user: { id: '1', displayName: 'user', roles: ['USER'] } } as TokenPair

describe('auth session races', () => {
  beforeEach(() => { setActivePinia(createPinia()); vi.resetAllMocks() })
  it('does not resurrect a logged-out session after a delayed refresh', async () => {
    const store = useAuthStore()
    api.login.mockResolvedValue(pair)
    await store.login({ loginName: 'user', password: 'password' })
    let finish!: (value: TokenPair) => void
    api.refresh.mockImplementation(() => new Promise((resolve) => { finish = resolve }))
    const refresh = store.refresh()
    const rejected = expect(refresh).rejects.toThrow('会话已变更')
    await store.logout()
    finish({ ...pair, accessToken: 'late-access' })
    await rejected
    expect(store.authenticated).toBe(false)
    expect(store.accessToken).toBeNull()
    expect(api.logout).toHaveBeenCalledWith('refresh-1', 'access-1')
  })
  it('keeps the newest login when an earlier login completes later', async () => {
    const store = useAuthStore()
    let finish!: (value: TokenPair) => void
    api.login.mockImplementationOnce(() => new Promise((resolve) => { finish = resolve }))
    const first = store.login({ loginName: 'first', password: 'password' })
    api.login.mockResolvedValueOnce({ ...pair, accessToken: 'new-access' })
    await store.login({ loginName: 'second', password: 'password' })
    finish(pair)
    await first
    expect(store.accessToken).toBe('new-access')
  })
  it('does not clear a new login when an older logout finishes', async () => {
    const store = useAuthStore()
    api.login.mockResolvedValue(pair)
    await store.login({ loginName: 'user', password: 'password' })
    let finish!: () => void
    api.logout.mockImplementation(() => new Promise<void>((resolve) => { finish = resolve }))
    const logout = store.logout()
    api.login.mockResolvedValue({ ...pair, accessToken: 'new-access' })
    await store.login({ loginName: 'new-user', password: 'password' })
    finish()
    await logout
    expect(store.accessToken).toBe('new-access')
  })
})
