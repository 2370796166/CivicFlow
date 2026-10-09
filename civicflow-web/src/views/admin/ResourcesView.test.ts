import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ResourcesView from './ResourcesView.vue'
const resources = vi.fn()
const windowItems = vi.fn(), bindWindowItems = vi.fn(), confirm = vi.fn()
vi.mock('@/api/admin', () => ({ adminApi: { resources: (...args: unknown[]) => resources(...args), windowItems: (...args: unknown[]) => windowItems(...args), bindWindowItems: (...args: unknown[]) => bindWindowItems(...args) } }))
vi.mock('element-plus', () => ({ ElMessage: { error: vi.fn() }, ElMessageBox: { confirm: (...args: unknown[]) => confirm(...args) } }))
const window = { id: '3', outletId: '2', code: 'W1', name: '一号窗口', status: 'ENABLED', version: 0 }
const bindingStubs = {
  StatePanel: true, WindowStaffDialog: true, ElInput: true, ElSelect: true, ElOption: true, ElPagination: true,
  ElTable: { template: '<div><slot /></div>' }, ElTableColumn: { data: () => ({ row: window }), template: '<div><slot :row="row" /></div>' },
  ElDialog: { props: ['modelValue'], template: '<div v-if="modelValue"><slot /></div>' }, ElCheckboxGroup: { template: '<div><slot /></div>' }, ElCheckbox: true,
  ElButton: { props: ['disabled', 'loading'], template: '<button :disabled="disabled || loading" @click="$emit(\'click\')"><slot /></button>' },
}
describe('ResourcesView', () => {
  beforeEach(() => { resources.mockReset(); windowItems.mockReset(); bindWindowItems.mockReset(); confirm.mockReset(); confirm.mockResolvedValue('confirm') })
  it('uses server pagination and shows an explicit empty state', async () => {
    resources.mockResolvedValue({ items: [], page: 1, size: 20, total: 0 })
    const wrapper = mount(ResourcesView, { props: { kind: 'outlets' }, global: { stubs: { StatePanel: { props: ['title'], template: '<div>{{ title }}</div>' }, ElInput: true, ElSelect: true, ElOption: true, ElButton: true, ElTable: true, ElTableColumn: true, ElPagination: true, ElDialog: true, ElCheckboxGroup: true, ElCheckbox: true } } })
    await flushPromises()
    expect(resources).toHaveBeenCalledWith('outlets', expect.objectContaining({ page: 1, size: 20 }))
    expect(wrapper.text()).toContain('没有符合条件的记录')
    wrapper.unmount()
  })
  it('preserves existing item bindings and reuses a write key after a lost response', async () => {
    resources.mockImplementation(async (kind: string) => ({ items: kind === 'windows' ? [window] : [{ id: '9', name: '户籍办理' }], total: 1 }))
    windowItems.mockResolvedValue({ windowId: '3', version: 4, itemIds: ['9'] })
    bindWindowItems.mockRejectedValueOnce(new Error('timeout')).mockResolvedValueOnce({})
    const wrapper = mount(ResourcesView, { props: { kind: 'windows' }, global: { stubs: bindingStubs } }); await flushPromises()
    await wrapper.findAll('button').find(button => button.text() === '绑定事项')!.trigger('click'); await flushPromises()
    expect(wrapper.text()).toContain('已选择 1 项')
    const save = () => wrapper.findAll('button').find(button => button.text() === '确认替换')!
    await save().trigger('click'); await flushPromises()
    await save().trigger('click'); await flushPromises()
    expect(bindWindowItems.mock.calls[0]?.[0]).toEqual(expect.objectContaining({ id: '3', version: 4 }))
    expect(bindWindowItems.mock.calls[0]?.[1]).toEqual(['9'])
    expect(bindWindowItems.mock.calls[1]?.[2]).toBe(bindWindowItems.mock.calls[0]?.[2])
    wrapper.unmount()
  })
  it('blocks item replacement when the existing configuration cannot be read', async () => {
    resources.mockResolvedValue({ items: [window], total: 1 }); windowItems.mockRejectedValue(new Error('read failed'))
    const wrapper = mount(ResourcesView, { props: { kind: 'windows' }, global: { stubs: bindingStubs } }); await flushPromises()
    await wrapper.findAll('button').find(button => button.text() === '绑定事项')!.trigger('click'); await flushPromises()
    const save = wrapper.findAll('button').find(button => button.text() === '确认替换')!
    expect(save.attributes('disabled')).toBeDefined(); await save.trigger('click')
    expect(bindWindowItems).not.toHaveBeenCalled()
    wrapper.unmount()
  })
})
