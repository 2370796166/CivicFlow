import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import ReservationView from './ReservationView.vue'

const reservation = vi.fn()
const replace = vi.fn()
vi.mock('@/api/user', () => ({ userApi: { reservation: (...args: unknown[]) => reservation(...args) } }))
vi.mock('vue-router', () => ({ useRoute: () => ({ params: { reservationId: 'res-1' } }), useRouter: () => ({ replace }), RouterLink: { template: '<a><slot /></a>' } }))

describe('ReservationView', () => {
  afterEach(() => { vi.useRealTimers(); reservation.mockReset(); replace.mockReset() })
  it('stops polling on server failure', async () => {
    vi.useFakeTimers()
    reservation.mockResolvedValue({ reservationId: 'res-1', status: 'FAILED', appointment: null, failureCode: 'APPT_409_SLOT_FULL' })
    const wrapper = mount(ReservationView, { global: { stubs: { StatePanel: { props: ['description'], template: '<div>{{ description }}</div>' }, ElButton: true, RouterLink: true } } })
    await flushPromises()
    expect(wrapper.text()).toContain('APPT_409_SLOT_FULL')
    await vi.advanceTimersByTimeAsync(120000)
    expect(reservation).toHaveBeenCalledTimes(1)
  })
  it('navigates when the server returns an appointment', async () => {
    reservation.mockResolvedValue({ reservationId: 'res-1', status: 'PENDING_CONFIRM', appointment: { appointmentId: '456' }, failureCode: null })
    mount(ReservationView, { global: { stubs: { StatePanel: true, ElButton: true, RouterLink: true } } })
    await flushPromises()
    expect(replace).toHaveBeenCalledWith('/user/appointments/456')
  })
})
