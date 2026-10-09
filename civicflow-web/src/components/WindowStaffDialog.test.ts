import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import WindowStaffDialog from './WindowStaffDialog.vue'

const windowStaff = vi.fn(), bind = vi.fn(), confirm = vi.fn()
vi.mock('@/api/admin', () => ({ adminApi: { windowStaff: (...args: unknown[]) => windowStaff(...args), bindWindowStaff: (...args: unknown[]) => bind(...args), users: vi.fn(async () => ({ items: [{ id: '30', username: 'staff', displayName: '张老师', status: 'ENABLED', roles: ['STAFF'] }], total: 1 })) } }))
vi.mock('element-plus', () => ({ ElMessageBox: { confirm: (...args: unknown[]) => confirm(...args) } }))
const window = { id: '3', outletId: '2', name: '一号窗口', code: 'W1', status: 'ENABLED' as const, version: 0 }
const stubs = { ElDialog: { props: ['modelValue'], template: '<div v-if="modelValue"><slot /></div>' }, ElInput: true, ElCheckboxGroup: { template: '<div><slot /></div>' }, ElCheckbox: { template: '<span><slot /></span>' }, ElPagination: true, ElButton: { props: ['disabled', 'loading'], template: '<button :disabled="disabled || loading" @click="$emit(\'click\')"><slot /></button>' } }
const wrappers: ReturnType<typeof mount>[] = []
async function ready() {
  const wrapper = mount(WindowStaffDialog, { props: { window, modelValue: false }, global: { stubs } }); wrappers.push(wrapper)
  await wrapper.setProps({ modelValue: true }); await flushPromises(); return wrapper
}
function save(wrapper: ReturnType<typeof mount>) { return wrapper.findAll('button').find(button => button.text() === '保存授权')! }
describe('Window staff assignments', () => {
  beforeEach(() => { windowStaff.mockReset(); bind.mockReset(); confirm.mockReset(); windowStaff.mockResolvedValue({ windowId: '3', version: 4, staffUserIds: ['30'], inheritedStaffUserIds: ['31'] }); confirm.mockResolvedValue('confirm') })
  afterEach(() => { for (const wrapper of wrappers.splice(0)) wrapper.unmount() })
  it('loads existing selections and saves against the latest server version', async () => {
    bind.mockResolvedValue({}); const wrapper = await ready()
    expect(wrapper.text()).toContain('张老师'); expect(wrapper.text()).toContain('31')
    await save(wrapper).trigger('click'); await flushPromises()
    expect(bind).toHaveBeenCalledWith('3', ['30'], 4, expect.any(String))
    expect(wrapper.emitted('updated')).toHaveLength(1)
  })
  it('reuses its key after a lost write response', async () => {
    bind.mockRejectedValueOnce(new Error('timeout')).mockResolvedValueOnce({})
    const wrapper = await ready(); await save(wrapper).trigger('click'); await flushPromises(); await save(wrapper).trigger('click'); await flushPromises()
    expect(bind.mock.calls[1]?.[3]).toBe(bind.mock.calls[0]?.[3])
  })
  it('requires a fresh read after a version conflict', async () => {
    bind.mockRejectedValue(Object.assign(new Error('conflict'), { status: 409 }))
    const wrapper = await ready(); await save(wrapper).trigger('click'); await flushPromises()
    expect(wrapper.text()).toContain('重新读取授权'); expect(save(wrapper).attributes('disabled')).toBeDefined()
    await save(wrapper).trigger('click'); expect(bind).toHaveBeenCalledTimes(1)
  })
})
