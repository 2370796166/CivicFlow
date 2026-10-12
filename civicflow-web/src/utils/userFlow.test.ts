import { describe, expect, it } from 'vitest'
import { canCancel, checkInDateHint, queueTerminal, remaining, reservationTerminal } from './userFlow'

describe('user flow display rules', () => {
  it('uses the Guangzhou calendar date across the UTC midnight boundary', () => {
    expect(checkInDateHint('2026-10-03', Date.parse('2026-10-02T16:01:00Z'))).toBe('')
    expect(checkInDateHint('2026-10-04', Date.parse('2026-10-02T16:01:00Z'))).toContain('预约当天')
    expect(checkInDateHint('2026-10-02', Date.parse('2026-10-02T16:01:00Z'))).toContain('已过')
  })
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
