import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AdminResourceSelect from './AdminResourceSelect.vue'
import type { AdminResource } from '@/types/admin'
import type { Page } from '@/types/api'

const resources = vi.fn(), resource = vi.fn()
vi.mock('@/api/admin', () => ({ adminApi: { resources: (...args: unknown[]) => resources(...args), resource: (...args: unknown[]) => resource(...args) } }))
const stubs = {
  ElSelect: {
    props: ['modelValue', 'remoteMethod', 'noDataText'], emits: ['visible-change', 'update:modelValue'],
    template: `<div><button @click="$emit('visible-change', true)">打开</button><input aria-label="搜索" @input="remoteMethod($event.target.value)"><select :value="modelValue" @change="$emit('update:modelValue', $event.target.value)"><option value="">请选择</option><slot /></select><p>{{ noDataText }}</p><slot name="footer" /></div>`,
  },
  ElOption: { props: ['value', 'label', 'disabled'], template: '<option :value="value" :disabled="disabled">{{ label }}</option>' },
  ElButton: { props: ['loading', 'disabled'], emits: ['click'], template: '<button :disabled="loading || disabled" @click="$emit(\'click\')"><slot /></button>' },
}
const item = (id: string, name = '业务咨询'): AdminResource => ({ id, name, code: `ITEM_${id}`, status: 'ENABLED', version: 0, description: null, defaultDurationMinutes: 30 })
const result = (items: AdminResource[], total = items.length, page = 1): Page<AdminResource> => ({ items, total, page, size: 20 })
const create = (props: { kind?: 'items' | 'outlets'; modelValue?: string; enabledOnly?: boolean } = {}) => mount(AdminResourceSelect, { props: { kind: 'items', ...props }, global: { stubs } })

describe('AdminResourceSelect', () => {
  beforeEach(() => { resources.mockReset().mockResolvedValue(result([])); resource.mockReset() })

  it('searches enabled resources and emits the original ID without losing precision', async () => {
    const id = '2106005882233079006'
    resources.mockResolvedValue(result([item(id)]))
    const wrapper = create()
    await wrapper.get('input').setValue('业务')
    await flushPromises()
    expect(resources).toHaveBeenCalledWith('items', expect.objectContaining({ keyword: '业务', status: 'ENABLED', page: 1, size: 20 }))
    expect(wrapper.get(`option[value="${id}"]`).text()).toContain('业务咨询')
    await wrapper.get('select').setValue(id)
    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([id])
    await wrapper.setProps({ modelValue: id })
    expect(wrapper.emitted('selected')?.at(-1)?.[0]).toEqual(item(id))
    expect(resource).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('can load subsequent pages and retry without discarding the first page', async () => {
    const firstPage = Array.from({ length: 20 }, (_, index) => item(String(index + 1)))
    resources.mockResolvedValueOnce(result(firstPage, 21)).mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce(result([item('21', '第二页事项')], 21, 2))
    const wrapper = create({ enabledOnly: false })
    await wrapper.get('button').trigger('click'); await flushPromises()
    expect(resources).toHaveBeenCalledWith('items', expect.objectContaining({ status: undefined }))
    await wrapper.findAll('button').find(button => button.text() === '加载更多')!.trigger('click'); await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('选项加载失败')
    expect(wrapper.findAll('option')).toHaveLength(21)
    await wrapper.findAll('button').find(button => button.text() === '重试加载')!.trigger('click'); await flushPromises()
    expect(resources.mock.calls[2]?.[1]).toEqual(expect.objectContaining({ page: 2 }))
    expect(wrapper.findAll('option')).toHaveLength(22)
    expect(wrapper.text()).toContain('第二页事项')
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('ignores a slow response for a previous search', async () => {
    let resolveOld!: (value: Page<AdminResource>) => void
    resources.mockReturnValueOnce(new Promise<Page<AdminResource>>(resolve => { resolveOld = resolve })).mockResolvedValueOnce(result([item('2', '最新事项')]))
    const wrapper = create()
    await wrapper.get('input').setValue('旧'); await wrapper.get('input').setValue('最新'); await flushPromises()
    resolveOld(result([item('1', '旧事项')]))
    await flushPromises()
    expect(wrapper.text()).toContain('最新事项')
    expect(wrapper.text()).not.toContain('旧事项')
    wrapper.unmount()
  })

  it('does not duplicate the request when opening also triggers an empty search', async () => {
    let finish!: (value: Page<AdminResource>) => void
    resources.mockReturnValue(new Promise<Page<AdminResource>>(resolve => { finish = resolve }))
    const wrapper = create()
    await wrapper.get('button').trigger('click')
    await wrapper.get('input').trigger('input')
    expect(resources).toHaveBeenCalledTimes(1)
    finish(result([])); await flushPromises()
    wrapper.unmount()
  })

  it('resolves an existing selection outside the first page and keeps its label after searching', async () => {
    const existing = item('2106005882233079006', '原有事项')
    resource.mockResolvedValue(existing)
    resources.mockResolvedValue(result([item('2', '其他事项')]))
    const wrapper = create({ modelValue: existing.id })
    await flushPromises()
    expect(resource).toHaveBeenCalledWith('items', existing.id)
    await wrapper.get('input').setValue('其他'); await flushPromises()
    expect(wrapper.get(`option[value="${existing.id}"]`).text()).toContain('原有事项')
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    wrapper.unmount()
  })

  it('shows a retry action when the current resource cannot be read', async () => {
    resource.mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce(item('1'))
    const wrapper = create({ modelValue: '1' })
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('当前事项读取失败')
    await wrapper.findAll('button').find(button => button.text() === '重新读取')!.trigger('click'); await flushPromises()
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('业务咨询')
    wrapper.unmount()
  })
})
