import { describe, expect, it } from 'vitest'
import {
  beijingDate,
  plusDays,
  instant,
  dateAllowed,
  slotState,
  mergeStock,
  canCancel,
  latestAppointment,
} from '../src/utils/flow'
import type { Appointment, Rules, Slot } from '../src/types'
const now = Date.parse('2026-10-13T02:00:00Z')
const rules: Rules = {
  advanceDays: 30,
  closedDates: ['2026-10-14'],
  cancellationNoticeSeconds: 600,
}
const slot: Slot = {
  id: '9223372036854775806',
  outletId: '9007199254740999',
  itemId: '1',
  serviceDate: '2026-10-13',
  startTime: '11:00:00',
  endTime: '12:00:00',
  totalQuota: 500,
  releaseAt: '2026-10-13T01:00:00Z',
  closeAt: '2026-10-13T03:00:00Z',
  bookingStatus: 'BOOKABLE',
  status: 'OPEN',
  availability: 'BOOKABLE',
  reservable: true,
  remaining: 2,
  liveAvailability: 'AVAILABLE',
}
describe('booking semantics', () => {
  it('keeps the newest order version when a poll predates an action response', () => {
    const current = {
      appointmentId: '9223372036854775806',
      status: 'CONFIRMED',
      version: 3,
    } as Appointment
    const old = {
      ...current,
      status: 'PENDING_CONFIRM',
      version: 2,
    } as Appointment
    expect(latestAppointment(current, old)).toBe(current)
    expect(latestAppointment(old, current)).toBe(current)
  })
  it('preserves string IDs and does not substitute total quota for unknown stock', () => {
    const merged = mergeStock(
      [slot],
      [
        {
          configuration: { slotId: slot.id },
          remaining: null,
          availability: 'UNKNOWN',
        },
      ],
    )[0]!
    expect(merged.id).toBe('9223372036854775806')
    expect(merged.remaining).toBeNull()
    expect(slotState(merged, rules, now)).toEqual({
      label: '余量待核实，可尝试预约',
      enabled: true,
    })
    expect(mergeStock([slot], [])[0]!.remaining).toBeNull()
  })
  it('handles full stock, suspension, expiry and config/projection disagreements', () => {
    expect(slotState({ ...slot, remaining: 0 }, rules, now).enabled).toBe(false)
    expect(
      slotState({ ...slot, status: 'SUSPENDED' }, rules, now).enabled,
    ).toBe(false)
    expect(
      slotState({ ...slot, liveAvailability: 'CLOSED' }, rules, now).enabled,
    ).toBe(false)
    expect(
      slotState({ ...slot, bookingStatus: 'UPCOMING' }, rules, now).enabled,
    ).toBe(false)
    expect(slotState({ ...slot, reservable: false }, rules, now).enabled).toBe(
      false,
    )
    expect(slotState(slot, rules, Date.parse(slot.closeAt)).enabled).toBe(false)
  })
  it('applies Beijing dates and closed dates across UTC midnight', () => {
    expect(beijingDate(Date.parse('2026-10-12T16:00:00Z'))).toBe('2026-10-13')
    expect(instant('2026-10-12T16:01:00Z')).toBe('2026-10-13 00:01')
    expect(plusDays('2026-12-31', 1)).toBe('2027-01-01')
    expect(dateAllowed('2026-10-14', rules, now)).toBe(false)
    expect(dateAllowed('2026-11-13', rules, now)).toBe(false)
  })
  it('uses cancellation deadline and status without changing the order', () => {
    const a = {
      status: 'CONFIRMED',
      serviceDate: '2026-10-13',
      slotStartTime: '10:09:00',
    } as Appointment
    expect(canCancel(a, rules, now)).toBe(false)
    expect(canCancel({ ...a, slotStartTime: '11:00:00' }, rules, now)).toBe(
      true,
    )
    expect(canCancel({ ...a, status: 'CHECKED_IN' }, rules, now)).toBe(false)
  })
})
