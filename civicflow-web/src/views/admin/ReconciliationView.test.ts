import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ReconciliationView from './ReconciliationView.vue'
const mocks = vi.hoisted(() => ({ reconcile: vi.fn(), slots: vi.fn(), slot: vi.fn(), reconciliations: vi.fn(), reconciliation: vi.fn(), confirm: vi.fn() }))
vi.mock('@/api/admin', () => ({ adminApi: mocks }))
vi.mock('vue-router', () => ({ useRoute: () => ({ query: {} }) }))
vi.mock('element-plus', () => ({ ElMessageBox: { confirm: mocks.confirm }, ElMessage: { success: vi.fn() } }))
const stubs = { RouterLink: { template: '<a><slot /></a>' }, AdminPager: true, StatePanel: true, ElButton: { props: ['disabled'], emits: ['click'], template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>' } }
const report = { runId: '901', slotId: '123', expected: 8, actual: 10, diff: 2, configuredTotal: 10, persistedConsumed: 2, pendingReserved: 0, successfulCompensations: 0, pendingCompensations: 0, classification: 'REDIS_STOCK_MISMATCH', detectedAt: '2026-10-01T00:00:00Z', autoRepairable: true, repairStatus: 'MANUAL_REQUIRED' }
const button = (wrapper: ReturnType<typeof mount>, name: string) => wrapper.findAll('button').find(item => item.text() === name)!
beforeEach(() => { vi.clearAllMocks(); mocks.slots.mockResolvedValue({ items: [], total: 0 }); mocks.reconciliations.mockResolvedValue({ items: [], total: 0 }); mocks.reconcile.mockResolvedValue(report); mocks.confirm.mockResolvedValue('confirm') })
describe('administrator reconciliation', () => {
  it('requires fresh evidence and a confirmation before repairing', async () => {
    const wrapper = mount(ReconciliationView, { global: { stubs } }); await flushPromises()
    expect(button(wrapper, '安全修复').attributes('disabled')).toBeDefined()
    await wrapper.get('input[placeholder="也可直接输入完整 ID"]').setValue('123')
    await button(wrapper, '开始对账').trigger('click'); await flushPromises()
    expect(mocks.reconcile).toHaveBeenCalledWith('123', false)
    expect(button(wrapper, '安全修复').attributes('disabled')).toBeUndefined()
    await button(wrapper, '安全修复').trigger('click'); await flushPromises()
    expect(mocks.confirm).toHaveBeenCalledTimes(1)
    expect(mocks.reconcile).toHaveBeenCalledWith('123', true)
  })
  it('shows expired slot guidance and offers historical access', async () => {
    const slot = { id: '123', serviceDate: '2020-01-01', startTime: '09:00:00', endTime: '10:00:00', releaseAt: '2019-12-31T00:00:00Z', status: 'OPEN', totalQuota: 10 }
    mocks.slots.mockResolvedValue({ items: [slot], total: 1 }); mocks.slot.mockResolvedValue(slot)
    const wrapper = mount(ReconciliationView, { global: { stubs } }); await flushPromises()
    await wrapper.get('select').setValue('123'); await flushPromises()
    expect(wrapper.text()).toContain('服务时段已结束')
    expect(button(wrapper, '开始对账').attributes('disabled')).toBeDefined()
    await button(wrapper, '查看该号源历史').trigger('click'); await flushPromises()
    expect(mocks.reconciliations).toHaveBeenLastCalledWith(expect.objectContaining({ slotId: '123' }))
    expect(mocks.reconcile).not.toHaveBeenCalled()
  })
  it('never enables repair from an old report even when its saved flag permits it', async () => {
    const history = { runId: '901', slotId: '123', status: 'COMPLETED', triggerType: 'ADMIN', startedAt: '2026-10-01T00:00:00Z', report }
    mocks.reconciliations.mockResolvedValue({ items: [history], total: 1 }); mocks.reconciliation.mockResolvedValue(history)
    const wrapper = mount(ReconciliationView, { global: { stubs } }); await flushPromises()
    await wrapper.get('input[placeholder="也可直接输入完整 ID"]').setValue('123')
    await button(wrapper, '查看报告').trigger('click'); await flushPromises()
    expect(wrapper.text()).toContain('历史快照')
    expect(button(wrapper, '安全修复').attributes('disabled')).toBeDefined()
  })
})
