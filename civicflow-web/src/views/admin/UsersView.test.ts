import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import UsersView from './UsersView.vue'

const users = vi.fn(), createUser = vi.fn(), confirm = vi.fn()
vi.mock('@/api/admin', () => ({ adminApi: { users: (...args: unknown[]) => users(...args), createUser: (...args: unknown[]) => createUser(...args) } }))
vi.mock('element-plus', () => ({ ElMessage: { error: vi.fn() }, ElMessageBox: { confirm: (...args: unknown[]) => confirm(...args) } }))
const stubs = {
  StatePanel: true, ElInput: true, ElSelect: true, ElOption: true, ElTable: true, ElTableColumn: true, ElPagination: true,
  ElCheckboxGroup: true, ElCheckbox: true,
  ElDialog: { props: ['modelValue'], template: '<div v-if="modelValue"><slot /></div>' },
  ElButton: { props: ['disabled', 'loading'], emits: ['click'], template: '<button :disabled="disabled || loading" @click="$emit(\'click\')"><slot /></button>' },
}
async function openForm() {
  const wrapper = mount(UsersView, { global: { stubs } })
  await flushPromises()
  await wrapper.findAll('button').find(button => button.text() === '新增用户')!.trigger('click')
  const inputFor = (label: string) => wrapper.findAll('label').find(item => item.text() === label)!.get('input')
  await inputFor('用户名').setValue('demo_account')
  await inputFor('显示名称').setValue('演示账号')
  await inputFor('初始密码').setValue('fixture-password')
  return wrapper
}
describe('UsersView new user roles', () => {
  beforeEach(() => {
    users.mockReset().mockResolvedValue({ items: [], total: 0, page: 1, size: 20 })
    createUser.mockReset().mockResolvedValue({})
    confirm.mockReset().mockResolvedValue('confirm')
  })

  it('defaults to a single USER role and submits a one-element roles array', async () => {
    const wrapper = await openForm()
    expect(wrapper.findAll('input[type="radio"]:checked')).toHaveLength(1)
    expect(wrapper.get('input[type="radio"]:checked').attributes('value')).toBe('USER')
    await wrapper.get('form').trigger('submit'); await flushPromises()
    expect(createUser).toHaveBeenCalledWith(expect.objectContaining({ username: 'demo_account', roles: ['USER'] }))
    expect(confirm).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('replaces the previous role when another radio is selected and confirms elevated access', async () => {
    const wrapper = await openForm()
    await wrapper.get('input[value="STAFF"]').setValue(true)
    await wrapper.get('input[value="ADMIN"]').setValue(true)
    expect(wrapper.findAll('input[type="radio"]:checked')).toHaveLength(1)
    expect(wrapper.get('input[type="radio"]:checked').attributes('value')).toBe('ADMIN')
    await wrapper.get('form').trigger('submit'); await flushPromises()
    expect(confirm).toHaveBeenCalledWith(expect.stringContaining('管理员'), expect.any(String), expect.any(Object))
    expect(createUser).toHaveBeenCalledWith(expect.objectContaining({ roles: ['ADMIN'] }))
    wrapper.unmount()
  })

  it('does not create an elevated account if confirmation is cancelled and resets the next form', async () => {
    confirm.mockRejectedValueOnce(new Error('cancel'))
    const wrapper = await openForm()
    await wrapper.get('input[value="STAFF"]').setValue(true)
    await wrapper.get('form').trigger('submit'); await flushPromises()
    expect(createUser).not.toHaveBeenCalled()
    await wrapper.findAll('button').find(button => button.text() === '取消')!.trigger('click')
    await wrapper.findAll('button').find(button => button.text() === '新增用户')!.trigger('click')
    expect(wrapper.get('input[type="radio"]:checked').attributes('value')).toBe('USER')
    expect((wrapper.get('input[type="password"]').element as HTMLInputElement).value).toBe('')
    wrapper.unmount()
  })
})
