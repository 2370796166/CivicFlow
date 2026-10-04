import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import WorkbenchView from './WorkbenchView.vue'

const scopes = vi.fn()
const current = vi.fn()
const callNext = vi.fn()
const change = vi.fn()
const confirm = vi.fn()
const warning = vi.fn()
vi.mock('@/api/staff', () => ({ staffApi: { scopes: (...args: unknown[]) => scopes(...args), current: (...args: unknown[]) => current(...args), callNext: (...args: unknown[]) => callNext(...args), change: (...args: unknown[]) => change(...args) } }))
vi.mock('element-plus', () => ({ ElMessageBox: { confirm: (...args: unknown[]) => confirm(...args) }, ElMessage: { warning: (...args: unknown[]) => warning(...args) } }))

const scope = { outletId: 'o1', outletCode: 'EAST', outletName: '东城中心', windowId: 'w1', windowCode: '01', windowName: '一号窗口', items: [{ id: 'i1', code: 'H', name: '户籍' }] }
const session = { id: 's1', outletId: 'o1', windowId: 'w1', status: 'ACTIVE', version: 0, startedAt: '', endedAt: null }
const ticket = { id: 't1', appointmentId: 'a1', ticketNo: 'A001', status: 'CALLED', checkedInAt: '', calledWindowId: 'w1', workSessionId: 's1', callCount: 1, version: 0 }
const stubs = { StatePanel: { template: '<div />' } }

function button(wrapper: ReturnType<typeof mount>, label: string) { return wrapper.findAll('button').find((item) => item.text().includes(label))! }

describe('WorkbenchView', () => {
  beforeEach(() => { scopes.mockResolvedValue([scope]); current.mockReset(); callNext.mockReset(); change.mockReset(); confirm.mockReset(); warning.mockReset() })
  afterEach(() => { vi.restoreAllMocks() })

  it('locks all actions while the confirmation is open', async () => {
    current.mockResolvedValue({ session, currentTicket: null })
    let accept!: (value: unknown) => void
    confirm.mockImplementation(() => new Promise((resolve) => { accept = resolve }))
    callNext.mockResolvedValue(null)
    const wrapper = mount(WorkbenchView, { global: { stubs } })
    await flushPromises()
    const callButton = button(wrapper, '叫下一号')
    await callButton.trigger('click')
    await callButton.trigger('click')
    expect(confirm).toHaveBeenCalledTimes(1)
    expect(callNext).not.toHaveBeenCalled()
    accept('confirm')
    await flushPromises()
    expect(callNext).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('当前没有可叫的等待票')
    wrapper.unmount()
  })

  it('refreshes the current ticket after a state conflict', async () => {
    current.mockResolvedValueOnce({ session, currentTicket: ticket }).mockResolvedValueOnce({ session, currentTicket: { ...ticket, status: 'SERVING', version: 1 } })
    confirm.mockResolvedValue('confirm')
    change.mockRejectedValue(Object.assign(new Error('conflict'), { status: 409, code: 'QUEUE_409_STATE_CONFLICT' }))
    const wrapper = mount(WorkbenchView, { global: { stubs } })
    await flushPromises()
    await button(wrapper, '标记过号').trigger('click')
    await flushPromises()
    expect(current).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('状态已变化')
    expect(wrapper.text()).toContain('完成办理')
    wrapper.unmount()
  })
})
