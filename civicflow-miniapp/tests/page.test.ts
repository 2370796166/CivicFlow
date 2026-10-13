import { effectScope } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { usePaged } from '../src/composables/page'
import { createCredentialVault } from '../src/utils/credential'
import type { Page } from '../src/types'
const hooks = vi.hoisted(() => ({
  hide: [] as (() => void)[],
  unload: [] as (() => void)[],
}))
vi.mock('@dcloudio/uni-app', () => ({
  onHide: (fn: () => void) => hooks.hide.push(fn),
  onUnload: (fn: () => void) => hooks.unload.push(fn),
}))
beforeEach(() => {
  hooks.hide.length = 0
  hooks.unload.length = 0
  vi.stubGlobal('uni', { stopPullDownRefresh: vi.fn() })
})
describe('stale responses and credential cleanup', () => {
  it('does not let an old date or notification page overwrite the latest selection', async () => {
    const scope = effectScope(),
      callbacks: ((p: Page<string>) => void)[] = []
    const pager = scope.run(() =>
      usePaged(
        () => new Promise<Page<string>>((resolve) => callbacks.push(resolve)),
      ),
    )!
    const old = pager.load(1),
      fresh = pager.load(2)
    callbacks[1]!({ items: ['new date / page'], total: 21, page: 2, size: 20 })
    await fresh
    callbacks[0]!({ items: ['old date / page'], total: 21, page: 1, size: 20 })
    await old
    expect(pager.items.value).toEqual(['new date / page'])
    expect(pager.page.value).toBe(2)
    const hidden = pager.load(3)
    hooks.hide.forEach((fn) => fn())
    callbacks[2]!({ items: ['hidden response'], total: 40, page: 3, size: 20 })
    await hidden
    expect(pager.items.value).toEqual(['new date / page'])
    scope.stop()
  })
  it('expires credentials and ignores issuance completed after hide/logout/state change', () => {
    const vault = createCredentialVault(),
      now = Date.parse('2026-10-13T02:00:00Z'),
      token = { token: 'private-test', expiresAt: '2026-10-13T02:02:00Z' }
    expect(vault.accept(token, vault.generation(), now)).toBe(true)
    expect(vault.expire(now + 119000)).toBe(false)
    expect(vault.expire(now + 120000)).toBe(true)
    expect(vault.value.value).toBeNull()
    const issue = vault.generation()
    vault.clear()
    expect(vault.accept(token, issue, now)).toBe(false)
    expect(vault.accept(token, vault.generation(), now + 120000)).toBe(false)
  })
})
