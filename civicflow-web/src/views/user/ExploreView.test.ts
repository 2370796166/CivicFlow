import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ExploreView from './ExploreView.vue'

const slots = vi.fn(), reserve = vi.fn(), push = vi.fn()
vi.mock('@/api/user', () => ({ userApi: {
  outlet: vi.fn(async () => ({ id: '3', name: '东城中心', address: '东城路' })),
  items: vi.fn(async () => ({ items: [{ id: '2', name: '户籍办理' }], total: 1 })),
  slots: (...args: unknown[]) => slots(...args), reserve: (...args: unknown[]) => reserve(...args),
} }))
vi.mock('vue-router', () => ({ useRoute: () => ({ params: { outletId: '3' } }), useRouter: () => ({ push }) }))
const slot = { id: '1234567890123456789', outletId: '3', itemId: '2', serviceDate: '2026-10-10', startTime: '09:00:00', endTime: '09:30:00', totalQuota: 20, releaseAt: '2026-10-09T00:00:00Z', closeAt: '2026-10-10T01:30:00Z', bookingStatus: 'BOOKABLE' }
const stubs = { RouterLink: true, StatePanel: true, ElInput: true, ElPagination: true, ElButton: { props: ['disabled', 'loading'], template: '<button :disabled="disabled || loading" @click="$emit(\'click\')"><slot /></button>' } }
const wrappers: ReturnType<typeof mount>[] = []
async function ready() {
  const wrapper = mount(ExploreView, { global: { stubs } }); wrappers.push(wrapper)
  await flushPromises(); await wrapper.find('input[type="radio"]').setValue(); await wrapper.find('#service-date').setValue('2026-10-10'); await flushPromises()
  return wrapper
}
function bookButton(wrapper: ReturnType<typeof mount>) { return wrapper.findAll('button').find(button => button.text() === '预约此时段')! }

describe('ExploreView booking', () => {
  beforeEach(() => { vi.useFakeTimers(); vi.setSystemTime(new Date('2026-10-09T01:00:00Z')); slots.mockReset(); reserve.mockReset(); push.mockReset(); slots.mockResolvedValue({ items: [slot], total: 1 }) })
  afterEach(() => { for (const wrapper of wrappers.splice(0)) wrapper.unmount(); vi.useRealTimers() })
  it('submits a string slot ID once and enters the reservation result page', async () => {
    let finish!: (result: unknown) => void
    reserve.mockImplementation(() => new Promise(resolve => { finish = resolve }))
    const wrapper = await ready()
    await bookButton(wrapper).trigger('click'); await bookButton(wrapper).trigger('click')
    expect(reserve).toHaveBeenCalledTimes(1)
    expect(reserve.mock.calls[0]?.[0]).toBe(slot.id)
    finish({ reservationId: 'res-1', status: 'CREATING' }); await flushPromises()
    expect(push).toHaveBeenCalledWith('/user/reservations/res-1')
  })
  it('reuses the same idempotency key after a lost response', async () => {
    reserve.mockRejectedValueOnce(new Error('network timeout')).mockResolvedValueOnce({ reservationId: 'res-2', status: 'CREATING' })
    const wrapper = await ready()
    await bookButton(wrapper).trigger('click'); await flushPromises()
    expect(wrapper.text()).toContain('请求结果暂不确定')
    await bookButton(wrapper).trigger('click'); await flushPromises()
    expect(reserve.mock.calls[1]?.[1]).toBe(reserve.mock.calls[0]?.[1])
    expect(push).toHaveBeenCalledWith('/user/reservations/res-2')
  })
  it('creates a fresh request after a definite business rejection', async () => {
    reserve.mockRejectedValueOnce(Object.assign(new Error('not open'), { status: 422, code: 'APPT_422_SLOT_NOT_OPEN' }))
      .mockResolvedValueOnce({ reservationId: 'res-3', status: 'CREATING' })
    const wrapper = await ready(); await bookButton(wrapper).trigger('click'); await flushPromises()
    expect(wrapper.text()).toContain('暂不可预约')
    await bookButton(wrapper).trigger('click'); await flushPromises()
    expect(reserve.mock.calls[1]?.[1]).not.toBe(reserve.mock.calls[0]?.[1])
    expect(push).toHaveBeenCalledWith('/user/reservations/res-3')
  })
  it('ignores an old date response after the user selects a new date', async () => {
    let oldResult!: (result: unknown) => void
    slots.mockImplementationOnce(() => new Promise(resolve => { oldResult = resolve }))
      .mockResolvedValueOnce({ items: [{ ...slot, startTime: '10:00:00', endTime: '10:30:00' }], total: 1 })
    const wrapper = await ready()
    await wrapper.find('#service-date').setValue('2026-10-11'); await flushPromises()
    oldResult({ items: [slot], total: 1 }); await flushPromises()
    expect(wrapper.text()).toContain('10:00–10:30')
    expect(wrapper.text()).not.toContain('09:00–09:30')
  })
  it('keeps upcoming and suspended slots unavailable for submission', async () => {
    slots.mockResolvedValue({ items: [{ ...slot, bookingStatus: 'UPCOMING' }, { ...slot, id: '9', bookingStatus: 'SUSPENDED' }], total: 2 })
    const wrapper = await ready()
    for (const button of wrapper.findAll('button').filter(button => ['尚未放号', '暂停预约'].includes(button.text()))) {
      expect(button.attributes('disabled')).toBeDefined(); await button.trigger('click')
    }
    expect(reserve).not.toHaveBeenCalled()
  })
})
