import { describe, expect, it } from 'vitest'
import { actionSignature, availableActions } from './staffFlow'
import type { CurrentWorkSession } from '@/types/staff'

const session: CurrentWorkSession['session'] = { id: 's1', outletId: 'o1', windowId: 'w1', status: 'ACTIVE', version: 2, startedAt: '', endedAt: null }

describe('staff action availability', () => {
  it('requires a session before calling and keeps end unavailable with a live ticket', () => {
    expect(availableActions(null)).toEqual(['start'])
    expect(availableActions({ session, currentTicket: null })).toEqual(['call-next', 'end'])
    const called: CurrentWorkSession = { session, currentTicket: { id: 't1', appointmentId: 'a1', ticketNo: 'A001', status: 'CALLED', checkedInAt: '', calledWindowId: 'w1', workSessionId: 's1', callCount: 1, version: 3 } }
    expect(availableActions(called)).toEqual(['recall', 'start-serving', 'miss'])
    expect(actionSignature('miss', called, 'w1')).toBe('miss:s1:t1:3')
  })
})
