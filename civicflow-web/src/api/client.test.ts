import axios, { AxiosError } from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { api, setUnauthorizedHandler } from './client'
import { pinia } from '@/stores/pinia'
import { useAuthStore } from '@/stores/auth'
import type { TokenPair } from '@/types/api'

const authApi = vi.hoisted(() => ({ login: vi.fn(), refresh: vi.fn(), logout: vi.fn() }))
vi.mock('@/api/auth', () => ({ authApi }))
vi.mock('element-plus', () => ({ ElMessage: { error: vi.fn() } }))
const pair = (id: string) => ({ accessToken: `access-${id}`, refreshToken: `refresh-${id}`, expiresIn: 900, user: { id, displayName: id, roles: ['USER'] } }) as TokenPair

describe('requests from previous sessions', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    useAuthStore(pinia).clear()
  })
  it('rejects a successful response from an old account after a new login', async () => {
    const auth = useAuthStore(pinia)
    authApi.login.mockResolvedValue(pair('old'))
    await auth.login({ loginName: 'old', password: 'password' })
    let finish!: () => void
    let started!: () => void
    const entered = new Promise<void>((resolve) => { started = resolve })
    const request = api.get('/user/me', { adapter: (config) => new Promise((resolve) => {
      finish = () => resolve({ data: { account: 'old' }, status: 200, statusText: 'OK', headers: {}, config })
      started()
    }) })
    const rejected = expect(request).rejects.toSatisfy(axios.isCancel)
    await entered
    authApi.login.mockResolvedValue(pair('new'))
    await auth.login({ loginName: 'new', password: 'password' })
    finish()
    await rejected
    expect(auth.accessToken).toBe('access-new')
  })
  it('does not clear the new session or refresh it for an old 401', async () => {
    const auth = useAuthStore(pinia)
    const unauthorized = vi.fn()
    setUnauthorizedHandler(unauthorized)
    authApi.login.mockResolvedValue(pair('old'))
    await auth.login({ loginName: 'old', password: 'password' })
    let finish!: () => void
    let started!: () => void
    const entered = new Promise<void>((resolve) => { started = resolve })
    const request = api.get('/user/me', { adapter: (config) => new Promise((_, reject) => {
      finish = () => reject(new AxiosError('Unauthorized', '401', config, undefined, { data: {}, status: 401, statusText: 'Unauthorized', headers: {}, config }))
      started()
    }) })
    const rejected = expect(request).rejects.toMatchObject({ status: 401 })
    await entered
    authApi.login.mockResolvedValue(pair('new'))
    await auth.login({ loginName: 'new', password: 'password' })
    finish()
    await rejected
    expect(auth.accessToken).toBe('access-new')
    expect(authApi.refresh).not.toHaveBeenCalled()
    expect(unauthorized).not.toHaveBeenCalled()
  })
})
