import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import SlotsView from './SlotsView.vue'

const slots = vi.fn()
const batchSlots = vi.fn()
const confirm = vi.fn()
vi.mock('@/api/admin', () => ({ adminApi: { slots: (...args: unknown[]) => slots(...args), batchSlots: (...args: unknown[]) => batchSlots(...args) } }))
vi.mock('element-plus', () => ({ ElMessageBox: { confirm: (...args: unknown[]) => confirm(...args) }, ElMessage: { error: vi.fn() } }))

const stubs = {
  StatePanel: { props: ['title'], template: '<div>{{ title }}</div>' },
  ElInput: true, ElSelect: true, ElOption: true, ElCalendar: true, ElTable: true,
  ElTableColumn: true, ElPagination: true, ElDropdown: true, ElDropdownMenu: true,
  ElDropdownItem: true,
  ElDialog: { template: '<div><slot /></div>' },
  ElButton: { props: ['disabled'], emits: ['click'], template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>' },
}

describe('SlotsView', () => {
  beforeEach(() => {
    slots.mockReset().mockResolvedValue({ items: [], page: 1, size: 20, total: 0 })
    batchSlots.mockReset().mockResolvedValue({ created: 2, skipped: 1, failed: [] })
    confirm.mockReset().mockResolvedValue('confirm')
  })

  it('previews the batch range and confirms before submitting to the server', async () => {
    const wrapper = mount(SlotsView, { global: { stubs } })
    await flushPromises()
    expect(slots).toHaveBeenCalledWith(expect.objectContaining({ page: 1, size: 20 }))
    const batchButton = wrapper.findAll('button').find((button) => button.text() === '批量生成')
    await batchButton!.trigger('click')
    const inputFor = (label: string) => wrapper.findAll('label').find((item) => item.text().includes(label))!.get('input')
    await inputFor('网点 ID').setValue('123')
    await inputFor('事项 ID').setValue('456')
    await inputFor('开始日期').setValue('2026-10-01')
    await inputFor('结束日期').setValue('2026-10-03')
    expect(wrapper.text()).toContain('共 3 个服务日')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(confirm).toHaveBeenCalledWith(expect.stringContaining('3 个服务日'), expect.any(String), expect.any(Object))
    expect(batchSlots).toHaveBeenCalledWith(expect.objectContaining({ startDate: '2026-10-01', endDate: '2026-10-03', outletId: '123', itemId: '456' }))
    expect(wrapper.text()).toContain('新增 2，跳过 1，失败 0')
    wrapper.unmount()
  })
})
