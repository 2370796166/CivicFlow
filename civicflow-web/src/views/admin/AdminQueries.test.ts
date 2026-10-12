import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AdminAppointmentsView from './AdminAppointmentsView.vue'
import OperationLogsView from './OperationLogsView.vue'
const mocks = vi.hoisted(() => ({ appointments: vi.fn(), appointment: vi.fn(), userNames: vi.fn(), operationLogs: vi.fn() }))
vi.mock('@/api/admin', () => ({ adminApi: mocks }))
vi.mock('vue-router', () => ({ useRoute: () => ({ query: {} }) }))
const stubs = { RouterLink: { template: '<a><slot /></a>' }, AdminPager: true, StatePanel: { props: ['title'], template: '<div>{{ title }}</div>' }, ElDrawer: { props: ['modelValue'], template: '<div v-if="modelValue"><slot /></div>' }, ElButton: { props: ['disabled', 'nativeType'], emits: ['click'], template: '<button :type="nativeType || \'button\'" :disabled="disabled" @click="$emit(\'click\')"><slot /></button>' } }
const appointment = { userId: '101', appointment: { appointmentId: '2104878377833562113', outletName: '测试网点', itemName: '测试事项', serviceDate: '2026-10-02', slotStartTime: '09:00', slotEndTime: '10:00', status: 'CONFIRMED', createdAt: '2026-10-01T00:00:00Z' } }
beforeEach(() => { vi.clearAllMocks(); mocks.appointments.mockResolvedValue({ items: [appointment], total: 1 }); mocks.userNames.mockResolvedValue([{ id: '101', displayName: '陈嘉明' }]); mocks.operationLogs.mockResolvedValue({ items: [], total: 0 }) })
describe('administrator query pages', () => {
  it('queries server data with an exact ID and validates the date range', async () => {
    const wrapper = mount(AdminAppointmentsView, { global: { stubs } }); await flushPromises()
    expect(wrapper.text()).toContain('测试网点')
    expect(wrapper.get('tbody td').text()).toContain('陈嘉明')
    expect(wrapper.get('tbody td').text()).not.toContain('用户 101')
    expect(mocks.userNames).toHaveBeenCalledWith(['101'])
    await wrapper.get('input[placeholder="预约 ID、预约编号或名称"]').setValue('2104878377833562113')
    await wrapper.get('form').trigger('submit'); await flushPromises()
    expect(mocks.appointments).toHaveBeenLastCalledWith(expect.objectContaining({ keyword: '2104878377833562113' }))
    await wrapper.findAll('input[type=date]')[0]!.setValue('2026-10-03'); await wrapper.findAll('input[type=date]')[1]!.setValue('2026-10-02')
    const calls = mocks.appointments.mock.calls.length; await wrapper.get('form').trigger('submit')
    expect(wrapper.text()).toContain('开始日期不能晚于结束日期')
    expect(mocks.appointments).toHaveBeenCalledTimes(calls)
  })
  it('keeps appointments readable when the name lookup fails and never displays a user ID as the name', async () => {
    mocks.userNames.mockRejectedValue(new Error('unavailable'))
    const wrapper = mount(AdminAppointmentsView, { global: { stubs } }); await flushPromises()
    expect(wrapper.text()).toContain('用户姓名加载失败')
    expect(wrapper.get('tbody td').text()).toContain('姓名暂不可用')
    expect(wrapper.get('tbody td').text()).not.toContain('用户 101')
  })
  it('does not replace current names with a delayed name lookup from an earlier query', async () => {
    let resolveOld!: (value: unknown) => void
    mocks.userNames.mockImplementationOnce(() => new Promise(resolve => { resolveOld = resolve }))
    const wrapper = mount(AdminAppointmentsView, { global: { stubs } }); await flushPromises()
    mocks.appointments.mockResolvedValue({ items: [{ ...appointment, userId: '202' }], total: 1 })
    mocks.userNames.mockResolvedValue([{ id: '202', displayName: '林晓彤' }])
    await wrapper.get('form').trigger('submit'); await flushPromises()
    resolveOld([{ id: '101', displayName: '陈嘉明' }]); await flushPromises()
    expect(wrapper.get('tbody td').text()).toContain('林晓彤')
    expect(wrapper.get('tbody td').text()).not.toContain('陈嘉明')
  })
  it('drops old results when switching log sources before a request completes', async () => {
    let resolveOld!: (value: unknown) => void
    mocks.operationLogs.mockImplementationOnce(() => new Promise(resolve => { resolveOld = resolve }))
    const wrapper = mount(OperationLogsView, { global: { stubs } }); await flushPromises()
    await wrapper.findAll('nav button').find(button => button.text() === '排队叫号')!.trigger('click'); await flushPromises()
    resolveOld({ items: [{ id: '1', action: 'OLD_RESPONSE' }], total: 1 }); await flushPromises()
    expect(mocks.operationLogs).toHaveBeenLastCalledWith('queue', expect.objectContaining({ page: 1 }))
    expect(wrapper.text()).not.toContain('OLD_RESPONSE')
    expect(wrapper.text()).toContain('没有匹配的操作记录')
  })
})
