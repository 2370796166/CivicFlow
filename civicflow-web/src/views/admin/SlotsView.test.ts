import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import SlotsView from './SlotsView.vue'

const slots = vi.fn()
const batchSlots = vi.fn()
const createSlot = vi.fn()
const confirm = vi.fn()
vi.mock('@/api/admin', () => ({ adminApi: { slots: (...args: unknown[]) => slots(...args), batchSlots: (...args: unknown[]) => batchSlots(...args), createSlot: (...args: unknown[]) => createSlot(...args) } }))
vi.mock('element-plus', () => ({ ElMessageBox: { confirm: (...args: unknown[]) => confirm(...args) }, ElMessage: { error: vi.fn() } }))

const outletId = '2106005880956557825', itemId = '2106005882233079006'
const stubs = {
  AdminResourceSelect: {
    props: ['modelValue', 'kind'], emits: ['update:modelValue', 'selected'],
    data: () => ({ outletId, itemId }),
    template: `<select :value="modelValue" @change="$emit('update:modelValue', $event.target.value); $emit('selected', { id: $event.target.value, name: kind === 'outlets' ? '演示网点' : '业务咨询' })"><option value="">请选择</option><option :value="kind === 'outlets' ? outletId : itemId">{{ kind === 'outlets' ? '演示网点' : '业务咨询' }}</option></select>`,
  },
  StatePanel: { props: ['title'], template: '<div>{{ title }}</div>' },
  ElInput: true, ElSelect: true, ElOption: true, ElCalendar: true, ElTable: true,
  ElTableColumn: true, ElPagination: true, ElDropdown: true, ElDropdownMenu: true,
  ElDropdownItem: true,
  ElDialog: { props: ['modelValue'], template: '<div v-if="modelValue"><slot /></div>' },
  ElButton: { props: ['disabled'], emits: ['click'], template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>' },
}

describe('SlotsView', () => {
  beforeEach(() => {
    slots.mockReset().mockResolvedValue({ items: [], page: 1, size: 20, total: 0 })
    batchSlots.mockReset().mockResolvedValue({ created: 2, skipped: 1, failed: [] })
    createSlot.mockReset().mockResolvedValue({})
    confirm.mockReset().mockResolvedValue('confirm')
  })

  it('previews the batch range and confirms before submitting to the server', async () => {
    const wrapper = mount(SlotsView, { global: { stubs } })
    await flushPromises()
    expect(slots).toHaveBeenCalledWith(expect.objectContaining({ page: 1, size: 20 }))
    const batchButton = wrapper.findAll('button').find((button) => button.text() === '批量生成')
    await batchButton!.trigger('click')
    const inputFor = (label: string) => wrapper.findAll('label').find((item) => item.text().includes(label))!.get('input')
    await wrapper.get('label[for="slot-outlet"] select').setValue(outletId)
    await wrapper.get('label[for="slot-item"] select').setValue(itemId)
    await inputFor('开始日期').setValue('2026-10-01')
    await inputFor('结束日期').setValue('2026-10-03')
    expect(wrapper.text()).toContain('共 3 个服务日')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(confirm).toHaveBeenCalledWith(expect.stringContaining('3 个服务日'), expect.any(String), expect.any(Object))
    expect(confirm.mock.calls[0]?.[0]).toContain('演示网点')
    expect(confirm.mock.calls[0]?.[0]).toContain('业务咨询')
    expect(batchSlots).toHaveBeenCalledWith(expect.objectContaining({ startDate: '2026-10-01', endDate: '2026-10-03', outletId, itemId }))
    expect(wrapper.text()).toContain('新增 2，跳过 1，失败 0')
    wrapper.unmount()
  })

  it('requires a selection and submits exact string IDs for a single slot', async () => {
    const wrapper = mount(SlotsView, { global: { stubs } })
    await flushPromises()
    await wrapper.findAll('button').find(button => button.text() === '单日新增')!.trigger('click')
    await wrapper.get('form').trigger('submit')
    expect(wrapper.text()).toContain('请选择网点和事项')
    expect(createSlot).not.toHaveBeenCalled()
    await wrapper.get('label[for="slot-outlet"] select').setValue(outletId)
    await wrapper.get('label[for="slot-item"] select').setValue(itemId)
    const inputFor = (label: string) => wrapper.findAll('label').find(item => item.text().includes(label))!.get('input')
    await inputFor('服务日期').setValue('2030-10-01')
    await inputFor('放号时间').setValue('2030-10-01T08:00')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(createSlot).toHaveBeenCalledWith(expect.objectContaining({ outletId, itemId, serviceDate: '2030-10-01' }))
    wrapper.unmount()
  })
})
