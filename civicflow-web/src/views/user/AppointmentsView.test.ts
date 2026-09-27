import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AppointmentsView from './AppointmentsView.vue'

const list = vi.fn()
vi.mock('@/api/user', () => ({ userApi: { appointments: (...args: unknown[]) => list(...args) } }))

describe('AppointmentsView', () => {
  beforeEach(() => { list.mockReset() })
  it('shows a useful empty state', async () => {
    list.mockResolvedValue({ items: [], page: 1, size: 20, total: 0 })
    const wrapper = mount(AppointmentsView, { global: { stubs: { StatePanel: { props: ['state', 'title'], template: '<div>{{ title }}</div>' }, ElSelect: true, ElOption: true, ElButton: true, ElPagination: true, ElTag: true, RouterLink: true } } })
    await flushPromises()
    expect(wrapper.text()).toContain('暂无预约')
    expect(list).toHaveBeenCalledWith(1, undefined)
  })
  it('renders server status and detail link', async () => {
    list.mockResolvedValue({ items: [{ appointmentId: '123', itemName: '户籍办理', outletName: '东城中心', serviceDate: '2026-10-01', slotStartTime: '09:00:00', status: 'PENDING_CONFIRM' }], total: 1 })
    const wrapper = mount(AppointmentsView, { global: { stubs: { RouterLink: { props: ['to'], template: '<a :href="to"><slot /></a>' }, StatePanel: true, ElSelect: true, ElOption: true, ElButton: true, ElPagination: true, ElTag: { template: '<span><slot /></span>' } } } })
    await flushPromises()
    expect(wrapper.text()).toContain('待确认')
    expect(wrapper.get('a').attributes('href')).toBe('/user/appointments/123')
  })
})
