import { describe, expect, it, vi } from 'vitest'
import { userApi } from './user'

const post = vi.fn()
vi.mock('./client', () => ({ api: { post: (...args: unknown[]) => post(...args) } }))

describe('reservation submission', () => {
  it('continues with the server reservationId returned in a 503 body', async () => {
    const data = { reservationId: 'res-1', status: 'CREATING', pollAfterMs: 500, failureCode: 'APPT_503_PUBLISH_UNKNOWN' }
    post.mockRejectedValueOnce(Object.assign(new Error('publish unknown'), { status: 503, data }))
    await expect(userApi.reserve('123', 'stable-key')).resolves.toEqual(data)
    expect(post.mock.calls[0]?.[2]).toEqual({ headers: { 'Idempotency-Key': 'stable-key' } })
  })
  it('does not invent a reservationId after a transport timeout', async () => {
    post.mockRejectedValueOnce(Object.assign(new Error('timeout'), { status: undefined }))
    await expect(userApi.reserve('123', 'stable-key')).rejects.toThrow('timeout')
  })
})
