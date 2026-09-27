import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import StatePanel from './StatePanel.vue'

describe('StatePanel', () => {
  it('shows an empty state with guidance', () => {
    const wrapper = mount(StatePanel, { props: { state: 'empty' }, global: { stubs: { ElButton: true, ElSkeleton: true } } })
    expect(wrapper.text()).toContain('暂无内容')
    expect(wrapper.text()).toContain('内容准备好后会显示在这里')
  })
  it('emits retry from an error state', async () => {
    const wrapper = mount(StatePanel, { props: { state: 'error' }, global: { stubs: { ElButton: { template: '<button><slot /></button>' }, ElSkeleton: true } } })
    await wrapper.get('button').trigger('click')
    expect(wrapper.emitted('retry')).toHaveLength(1)
  })
})
