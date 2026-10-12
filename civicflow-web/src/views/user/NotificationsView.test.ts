import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import NotificationsView from './NotificationsView.vue'

const list = vi.fn()
vi.mock('element-plus', () => ({ ElMessage: { error: vi.fn(), success: vi.fn() } }))
const preference = vi.fn()
const read = vi.fn()
const savePreference = vi.fn()
vi.mock('@/api/notification', () => ({
  notificationApi: {
    list: (...args: unknown[]) => list(...args),
    preference: (...args: unknown[]) => preference(...args),
    read: (...args: unknown[]) => read(...args),
    savePreference: (...args: unknown[]) => savePreference(...args),
  },
}))

describe('NotificationsView', () => {
  beforeEach(() => {
    list.mockReset()
    preference.mockReset()
    read.mockReset()
    savePreference.mockReset()
    preference.mockResolvedValue(true)
    read.mockResolvedValue(undefined)
  })

  it('shows only server text and marks the owned message read', async () => {
    list.mockResolvedValue({ items: [{ id: '42', eventId: 'event', templateCode: 'APPOINTMENT_CREATED',
      templateVersion: 1, title: '预约待确认', body: '预约 42 已创建。', status: 'SENT',
      createdAt: '2026-09-29T00:00:00Z', sentAt: '2026-09-29T00:00:00Z', readAt: null }],
    total: 1, page: 1, size: 20 })
    const wrapper = mount(NotificationsView, { global: { directives: { loading: {} }, stubs: {
      ElSwitch: true, ElTag: true, ElPagination: true,
      ElButton: { template: '<button @click="$emit(\'click\')"><slot /></button>' },
    } } })
    await flushPromises()
    expect(wrapper.text()).toContain('预约 42 已创建。')
    await wrapper.get('button').trigger('click')
    await flushPromises()
    expect(read).toHaveBeenCalledWith('42')
  })
})
