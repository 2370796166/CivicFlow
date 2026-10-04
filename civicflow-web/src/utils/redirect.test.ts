import { describe, expect, it } from 'vitest'
import { safeRedirect } from './redirect'

describe('safeRedirect', () => {
  it('accepts authorized same-origin paths', () => expect(safeRedirect('/staff?tab=queue', ['/staff'])).toBe('/staff?tab=queue'))
  it('rejects other roles and external redirects', () => {
    expect(safeRedirect('/admin', ['/staff'])).toBeNull()
    expect(safeRedirect('//evil.example', ['/staff'])).toBeNull()
    expect(safeRedirect('/staff\\evil', ['/staff'])).toBeNull()
  })
})
