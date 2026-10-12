import { describe, expect, it } from 'vitest'
import { adminTime, validId, validateQuery, slotIssue } from './adminQueries'
import type { AdminSlot } from '@/types/admin'
describe('administrator query boundaries', () => {
  it('preserves 64-bit IDs and rejects values outside the backend range', () => {
    expect(validId('2104878377833562113')).toBe(true)
    expect(validId('9223372036854775808')).toBe(false)
    expect(validId('0')).toBe(false)
    expect(validateQuery({ slotId: 'abc', dateFrom: '', dateTo: '' })).toContain('ID')
    expect(validateQuery({ dateFrom: '2026-10-03', dateTo: '2026-10-02' })).toContain('开始日期')
  })
  it('uses Shanghai service time and explains released and expired slots', () => {
    const slot = { serviceDate: '2026-10-02', endTime: '10:00:00', releaseAt: '2026-10-01T00:00:00Z', status: 'OPEN' } as AdminSlot
    expect(slotIssue(slot, Date.parse('2026-10-02T02:00:00Z'))).toContain('已结束')
    expect(slotIssue(slot, Date.parse('2026-10-01T01:00:00Z'))).toBe('')
    expect(slotIssue(slot, Date.parse('2026-09-30T23:00:00Z'))).toContain('尚未放号')
    expect(adminTime('2026-10-01T16:00:00Z')).toContain('2026/10/02')
  })
})
