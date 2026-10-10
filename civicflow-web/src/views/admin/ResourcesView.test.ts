import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ResourcesView from './ResourcesView.vue'
const resources = vi.fn()
const windowItems = vi.fn(), bindWindowItems = vi.fn(), confirm = vi.fn()
const resource = vi.fn(), createResource = vi.fn(), updateResource = vi.fn()
vi.mock('@/api/admin', () => ({ adminApi: { resources: (...args: unknown[]) => resources(...args), resource: (...args: unknown[]) => resource(...args), createResource: (...args: unknown[]) => createResource(...args), updateResource: (...args: unknown[]) => updateResource(...args), windowItems: (...args: unknown[]) => windowItems(...args), bindWindowItems: (...args: unknown[]) => bindWindowItems(...args) } }))
vi.mock('element-plus', () => ({ ElMessage: { error: vi.fn(), success: vi.fn() }, ElMessageBox: { confirm: (...args: unknown[]) => confirm(...args) } }))
const window = { id: '3', outletId: '2', code: 'W1', name: '一号窗口', status: 'ENABLED', version: 0 }
const outletId = '2106005880956557825'
const bindingStubs = {
  AdminResourceSelect: { props: ['modelValue'], emits: ['update:modelValue'], data: () => ({ outletId }), template: `<select :value="modelValue" @change="$emit('update:modelValue', $event.target.value)"><option value="">请选择网点</option><option value="2">原有网点</option><option :value="outletId">演示服务中心</option></select>` },
  StatePanel: true, WindowStaffDialog: true, ElInput: true, ElSelect: true, ElOption: true, ElPagination: true,
  ElTable: { template: '<div><slot /></div>' }, ElTableColumn: { data: () => ({ row: window }), template: '<div><slot :row="row" /></div>' },
  ElDialog: { props: ['modelValue'], template: '<div v-if="modelValue"><slot /></div>' }, ElCheckboxGroup: { template: '<div><slot /></div>' }, ElCheckbox: true,
  ElButton: { props: ['disabled', 'loading'], emits: ['click'], template: '<button :disabled="disabled || loading" @click="$emit(\'click\')"><slot /></button>' },
}
describe('ResourcesView', () => {
  beforeEach(() => { resources.mockReset(); resource.mockReset(); createResource.mockReset().mockResolvedValue({}); updateResource.mockReset().mockResolvedValue({}); windowItems.mockReset(); bindWindowItems.mockReset(); confirm.mockReset(); confirm.mockResolvedValue('confirm') })
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

  it('requires an outlet selection and submits its exact string ID when creating a window', async () => {
    resources.mockResolvedValue({ items: [], total: 0 })
    const wrapper = mount(ResourcesView, { props: { kind: 'windows' }, global: { stubs: bindingStubs } })
    await flushPromises()
    await wrapper.findAll('button').find(button => button.text() === '新增')!.trigger('click')
    const inputFor = (label: string) => wrapper.findAll('label').find(item => item.text() === label)!.get('input')
    await inputFor('编码').setValue('DEMO_WINDOW')
    await inputFor('名称').setValue('一号窗口')
    await wrapper.get('form').trigger('submit')
    expect(wrapper.text()).toContain('请选择所属网点')
    expect(createResource).not.toHaveBeenCalled()
    await wrapper.get('label[for="window-outlet"] select').setValue(outletId)
    await wrapper.get('form').trigger('submit'); await flushPromises()
    expect(createResource).toHaveBeenCalledWith('windows', expect.objectContaining({ outletId, code: 'DEMO_WINDOW', name: '一号窗口' }))
    wrapper.unmount()
  })

  it('preserves the existing outlet and latest version when editing a window', async () => {
    resources.mockResolvedValue({ items: [window], total: 1 })
    resource.mockResolvedValue({ ...window, version: 8 })
    const wrapper = mount(ResourcesView, { props: { kind: 'windows' }, global: { stubs: bindingStubs } })
    await flushPromises()
    await wrapper.findAll('button').find(button => button.text() === '编辑')!.trigger('click'); await flushPromises()
    expect((wrapper.get('label[for="window-outlet"] select').element as HTMLSelectElement).value).toBe('2')
    await wrapper.get('form').trigger('submit'); await flushPromises()
    expect(updateResource).toHaveBeenCalledWith('windows', '3', expect.objectContaining({ outletId: '2', version: 8 }))
    wrapper.unmount()
  })
})
