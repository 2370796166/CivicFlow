import { describe, expect, it, vi } from 'vitest'
import { userApi } from './user'

const post = vi.fn()
const get = vi.fn()
vi.mock('./client', () => ({ api: { post: (...args: unknown[]) => post(...args), get: (...args: unknown[]) => get(...args) } }))

describe('reservation submission', () => {
  it('keeps slot pagination and joins versioned inventory by string ID', async () => {
    get.mockResolvedValueOnce({ data: { data: { items: [{ id: '1234567890123456789', bookingStatus: 'BOOKABLE' }], page: 2, size: 20, total: 21 } } })
      .mockResolvedValueOnce({ data: { data: [{ configuration: { slotId: '1234567890123456789' }, remaining: 0, availability: 'FULL' }] } })
    const result = await userApi.slots('1', '2', '2026-10-12', 2)
    expect(result.total).toBe(21)
    expect(result.items[0]?.remaining).toBe(0)
    expect(get.mock.calls[0]?.[1].params.page).toBe(2)
  })
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
