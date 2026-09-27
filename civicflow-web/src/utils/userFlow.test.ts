import { describe, expect, it } from 'vitest'
import { canCancel, queueTerminal, remaining, reservationTerminal } from './userFlow'

describe('user flow display rules', () => {
  it('stops reservation polling only on server terminal states', () => {
    expect(reservationTerminal.has('CREATING')).toBe(false)
    expect(reservationTerminal.has('PENDING_CONFIRM')).toBe(true)
    expect(reservationTerminal.has('FAILED')).toBe(true)
  })
  it('uses deadlines only for display', () => {
    expect(remaining('2026-09-25T00:05:00Z', Date.parse('2026-09-25T00:00:00Z'))).toBe('05:00')
    expect(remaining('2026-09-25T00:00:00Z', Date.parse('2026-09-25T00:01:00Z'))).toBe('00:00')
    expect(canCancel('PENDING_CONFIRM')).toBe(true)
    expect(queueTerminal.has('MISSED')).toBe(true)
  })
})
