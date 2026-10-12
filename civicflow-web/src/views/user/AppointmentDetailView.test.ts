import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import AppointmentDetailView from './AppointmentDetailView.vue'

const appointment = vi.fn()
const confirm = vi.fn()
const queue = vi.fn()
const checkInToken = vi.fn(), checkIn = vi.fn()
vi.mock('qrcode', () => ({ default: { toDataURL: vi.fn(async () => 'data:image/png;base64,dGVzdA==') } }))
vi.mock('@/api/user', () => ({ userApi: { appointment: (...args: unknown[]) => appointment(...args), confirm: (...args: unknown[]) => confirm(...args), queue: (...args: unknown[]) => queue(...args), checkInToken: (...args: unknown[]) => checkInToken(...args), checkIn: (...args: unknown[]) => checkIn(...args) } }))
vi.mock('vue-router', () => ({ useRoute: () => ({ params: { appointmentId: '42' } }), RouterLink: { template: '<a><slot /></a>' } }))
vi.mock('element-plus', () => ({ ElMessage: { success: vi.fn() } }))

const pending = {
  appointmentId: '42', reservationId: 'res-1', slotId: '9', outletId: '3', itemId: '2', outletName: '东城中心', itemName: '户籍办理',
  serviceDate: '2026-10-01', slotStartTime: '09:00:00', slotEndTime: '09:30:00', status: 'PENDING_CONFIRM',
  confirmDeadline: '2026-10-01T01:00:00Z', version: 2, createdAt: '', updatedAt: '',
}
const stubs = { RouterLink: true, StatePanel: true, ElTag: { template: '<span><slot /></span>' }, ElButton: { template: '<button @click="$emit(\'click\')"><slot /></button>' } }

describe('AppointmentDetailView', () => {
  afterEach(() => { appointment.mockReset(); confirm.mockReset(); queue.mockReset(); checkInToken.mockReset(); checkIn.mockReset(); vi.useRealTimers() })
  it('signs in from the page and removes the QR credential after success', async () => {
    vi.useFakeTimers()
    appointment.mockResolvedValue({ ...pending, status: 'CONFIRMED' })
    checkInToken.mockResolvedValue({ token: 'QR_PRIVATE_CANARY', expiresAt: new Date(Date.now() + 120000).toISOString() })
    checkIn.mockImplementation(async () => { appointment.mockResolvedValue({ ...pending, status: 'CHECKED_IN' }); return { ticketNo: 'A001' } })
    queue.mockResolvedValue([])
    const wrapper = mount(AppointmentDetailView, { global: { stubs } }); await flushPromises()
    await wrapper.findAll('button').find(button => button.text() === '获取二维码')!.trigger('click'); await flushPromises()
    expect(wrapper.text()).not.toContain('QR_PRIVATE_CANARY')
    await wrapper.findAll('button').find(button => button.text() === '现场签到')!.trigger('click'); await flushPromises()
    expect(checkIn).toHaveBeenCalledWith('3', 'QR_PRIVATE_CANARY')
    expect(wrapper.text()).toContain('签到成功，排队号 A001')
    expect(wrapper.find('img').exists()).toBe(false)
    wrapper.unmount()
  })
  it('observes external check-in and waits for delayed appointment completion', async () => {
    vi.useFakeTimers()
    appointment.mockResolvedValue({ ...pending, status: 'CONFIRMED' })
    queue.mockResolvedValue([{ ticket: { appointmentId: '42', status: 'WAITING', ticketNo: 'A001' }, aheadCount: 0 }])
    const wrapper = mount(AppointmentDetailView, { global: { stubs } })
    await flushPromises()
    appointment.mockResolvedValue({ ...pending, status: 'CHECKED_IN' })
    await vi.advanceTimersByTimeAsync(15000)
    await flushPromises()
    expect(wrapper.text()).toContain('排队进度')
    queue.mockResolvedValue([{ ticket: { appointmentId: '42', status: 'COMPLETED', ticketNo: 'A001' }, aheadCount: 0 }])
    await vi.advanceTimersByTimeAsync(15000)
    appointment.mockResolvedValue({ ...pending, status: 'COMPLETED' })
    await vi.advanceTimersByTimeAsync(15000)
    await flushPromises()
    expect(wrapper.text()).toContain('已完成')
    expect(wrapper.text()).not.toContain('排队进度')
    wrapper.unmount()
  })
  it('does not submit confirmation twice while the first request is pending', async () => {
    appointment.mockResolvedValue(pending)
    let finish!: (value: unknown) => void
    confirm.mockImplementation(() => new Promise((resolve) => { finish = resolve }))
    const wrapper = mount(AppointmentDetailView, { global: { stubs } })
    await flushPromises()
    const button = wrapper.findAll('button').find((item) => item.text().includes('确认预约'))!
    await button.trigger('click')
    await button.trigger('click')
    expect(confirm).toHaveBeenCalledTimes(1)
    expect(confirm.mock.calls[0]?.slice(0, 2)).toEqual(['42', 2])
    finish({ ...pending, status: 'CONFIRMED', version: 3 })
    await flushPromises()
    expect(wrapper.text()).toContain('已确认')
    wrapper.unmount()
  })
})
