import { describe, expect, it, vi } from 'vitest'
import { staffApi } from './staff'

const del = vi.fn()
const post = vi.fn()
vi.mock('./client', () => ({ api: { delete: (...args: unknown[]) => del(...args), post: (...args: unknown[]) => post(...args) } }))

describe('staff API contract', () => {
  it('sends version and idempotency key when ending a session', async () => {
    const session = { id: 's1', outletId: 'o1', windowId: 'w1', status: 'ACTIVE' as const, version: 2, startedAt: '', endedAt: null }
    del.mockResolvedValueOnce({ data: { data: { ...session, status: 'ENDED' } } })
    await staffApi.end(session, 'key-1')
    expect(del).toHaveBeenCalledWith('/staff/work-sessions/s1', { data: { version: 2 }, headers: { 'Idempotency-Key': 'key-1' }, suppressErrorToast: true })
  })
  it('maps start-serving to the existing start path', async () => {
    const ticket = { id: 't1', appointmentId: 'a1', ticketNo: 'A001', status: 'CALLED' as const, checkedInAt: '', calledWindowId: 'w1', workSessionId: 's1', callCount: 1, version: 3 }
    post.mockResolvedValueOnce({ data: { data: ticket } })
    await staffApi.change('start', ticket, 's1', 'key-2')
    expect(post).toHaveBeenCalledWith('/staff/queue-tickets/t1/start', { sessionId: 's1', version: 3 }, { headers: { 'Idempotency-Key': 'key-2' }, suppressErrorToast: true })
  })
})
