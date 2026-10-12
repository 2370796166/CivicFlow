import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ExploreView from './ExploreView.vue'
const { outlet, items, slots, reserve, push } = vi.hoisted(() => ({ outlet: vi.fn(), items: vi.fn(), slots: vi.fn(), reserve: vi.fn(), push: vi.fn() }))
vi.mock('@/api/user', () => ({ userApi: { outlet, items, slots, reserve } }))
vi.mock('vue-router', () => ({ useRoute: () => ({ params: { outletId: '12' } }), useRouter: () => ({ push }) }))
const slot = { configuration: { slotId: '101', outletId: '12', itemId: '13', serviceDate: '2026-10-12', startTime: '09:00:00', endTime: '10:00:00', releaseAt: '2020-01-01T00:00:00Z', closeAt: '2099-01-01T00:00:00Z', totalQuota: 5 }, remaining: 2, availability: 'AVAILABLE' }
const button = { props: ['disabled'], template: '<button :disabled="disabled"><slot /></button>' }
function render() { return mount(ExploreView, { global: { stubs: { RouterLink: { template: '<a><slot /></a>' }, ElButton: button, ElPagination: true, StatePanel: true } } }) }
async function select(wrapper: ReturnType<typeof render>) { await flushPromises(); await wrapper.find('input[type="radio"]').setValue(); await wrapper.find('input[type="date"]').setValue('2026-10-12'); await flushPromises() }
describe('ExploreView reservation flow', () => {
  beforeEach(() => { sessionStorage.clear(); vi.clearAllMocks(); outlet.mockResolvedValue({ id: '12', name: '测试网点', address: '测试地址' }); items.mockResolvedValue({ items: [{ id: '13', name: '事项' }], total: 1 }); slots.mockResolvedValue([slot]) })
  afterEach(() => vi.useRealTimers())
  it('reads real slots and submits the selected slot with an idempotency key', async () => {
    reserve.mockResolvedValue({ reservationId: 'res-1', status: 'CREATING' })
    const wrapper = render(); await select(wrapper)
    expect(slots).toHaveBeenCalledWith('12', '13', '2026-10-12')
    const book = wrapper.findAll('button').find((item) => item.text() === '预约')!
    await book.trigger('click'); await flushPromises()
    expect(reserve).toHaveBeenCalledWith('101', expect.any(String))
    expect(push).toHaveBeenCalledWith('/user/reservations/res-1'); wrapper.unmount()
  })
  it('retains the original intent across an unknown network outcome', async () => {
    reserve.mockRejectedValueOnce(new Error('network')).mockResolvedValueOnce({ reservationId: 'res-2', status: 'CREATING' })
    const wrapper = render(); await select(wrapper)
    await wrapper.findAll('button').find((item) => item.text() === '预约')!.trigger('click'); await flushPromises()
    const first = reserve.mock.calls[0]![1]
    await wrapper.findAll('button').find((item) => item.text() === '预约')!.trigger('click'); await flushPromises()
    expect(reserve.mock.calls[1]![1]).toBe(first); wrapper.unmount()
  })
  it('disables booking when inventory is exhausted', async () => {
    slots.mockResolvedValue([{ ...slot, remaining: 0, availability: 'FULL' }])
    const wrapper = render(); await select(wrapper)
    expect(wrapper.findAll('button').find((item) => item.text() === '预约')!.attributes('disabled')).toBeDefined(); wrapper.unmount()
  })
})
