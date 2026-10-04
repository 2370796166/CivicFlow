import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import ReconciliationView from './ReconciliationView.vue'
const reconcile = vi.fn()
const confirm = vi.fn()
vi.mock('@/api/admin', () => ({ adminApi: { reconcile: (...args: unknown[]) => reconcile(...args) } }))
vi.mock('element-plus', () => ({ ElMessageBox: { confirm: (...args: unknown[]) => confirm(...args) }, ElMessage: { success: vi.fn() } }))
const stubs = { ElInput: { props: ['modelValue'], emits: ['update:modelValue'], template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />' }, ElButton: { props: ['disabled'], emits: ['click'], template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>' } }
describe('ReconciliationView', () => {
  it('requires a report before enabling safety repair, then confirms the repair', async () => {
    const report = { runId: 'r1', slotId: '123', expected: 8, actual: 10, diff: 2, configuredTotal: 10, persistedConsumed: 2, pendingReserved: 0, successfulCompensations: 0, pendingCompensations: 0, classification: 'REDIS_STOCK_MISMATCH', detectedAt: '', autoRepairable: true, repairStatus: 'NOT_ATTEMPTED' }
    reconcile.mockResolvedValue(report)
    confirm.mockResolvedValue('confirm')
    const wrapper = mount(ReconciliationView, { global: { stubs } })
    expect(wrapper.findAll('button')[1]?.attributes('disabled')).toBeDefined()
    await wrapper.get('input').setValue('123')
    await wrapper.findAll('button')[0]!.trigger('click')
    await flushPromises()
    expect(reconcile).toHaveBeenCalledWith('123', false)
    await wrapper.findAll('button')[1]!.trigger('click')
    await flushPromises()
    expect(confirm).toHaveBeenCalledTimes(1)
    expect(reconcile).toHaveBeenCalledWith('123', true)
  })
})
