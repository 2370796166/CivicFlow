import { describe, expect, it } from 'vitest'
import { chinaInstant, chinaLocal, dayCount, validateSlotTimes } from './slotForm'
describe('slot form boundaries', () => {
  it('counts inclusive days and rejects oversized batch scope', () => { expect(dayCount('2026-10-01', '2026-10-31')).toBe(31); expect(dayCount('2026-10-01', '2026-11-01')).toBe(32) })
  it('converts Shanghai civil time to UTC for the API', () => { expect(chinaInstant('2026-10-01T08:00')).toBe('2026-10-01T00:00:00.000Z'); expect(chinaLocal('2026-10-01T00:00:00Z')).toBe('2026-10-01T08:00') })
  it('checks slot and check-in ordering', () => { expect(validateSlotTimes('09:00', '09:30', '08:30', '09:30')).toBe(''); expect(validateSlotTimes('09:00', '09:30', '09:15', '09:30')).not.toBe('') })
})
