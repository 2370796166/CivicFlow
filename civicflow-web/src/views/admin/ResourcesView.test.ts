import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import ResourcesView from './ResourcesView.vue'
const resources = vi.fn()
vi.mock('@/api/admin', () => ({ adminApi: { resources: (...args: unknown[]) => resources(...args) } }))
describe('ResourcesView', () => {
  it('uses server pagination and shows an explicit empty state', async () => {
    resources.mockResolvedValue({ items: [], page: 1, size: 20, total: 0 })
    const wrapper = mount(ResourcesView, { props: { kind: 'outlets' }, global: { stubs: { StatePanel: { props: ['title'], template: '<div>{{ title }}</div>' }, ElInput: true, ElSelect: true, ElOption: true, ElButton: true, ElTable: true, ElTableColumn: true, ElPagination: true, ElDialog: true, ElCheckboxGroup: true, ElCheckbox: true } } })
    await flushPromises()
    expect(resources).toHaveBeenCalledWith('outlets', expect.objectContaining({ page: 1, size: 20 }))
    expect(wrapper.text()).toContain('没有符合条件的记录')
    wrapper.unmount()
  })
})
