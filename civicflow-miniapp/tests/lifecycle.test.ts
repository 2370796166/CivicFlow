import { afterEach, describe, expect, it, vi } from 'vitest'
import { createPoller } from '../src/utils/poller'
import { qrMatrix } from '../src/utils/qr'
import { deliveryStatuses, queueStatuses } from '../src/utils/flow'
afterEach(() => {
  vi.useRealTimers()
})
describe('lifecycle and QR', () => {
  it('stops on hide and does not accumulate timers when resumed', async () => {
    vi.useFakeTimers()
    const work = vi.fn(async () => false),
      poller = createPoller(work, 1000)
    poller.start()
    await vi.advanceTimersByTimeAsync(1000)
    expect(work).toHaveBeenCalledTimes(2)
    poller.stop()
    await vi.advanceTimersByTimeAsync(5000)
    expect(work).toHaveBeenCalledTimes(2)
    poller.start()
    poller.start()
    await vi.advanceTimersByTimeAsync(1000)
    expect(work).toHaveBeenCalledTimes(5)
    poller.stop()
    expect(vi.getTimerCount()).toBe(0)
  })
  it('does not reschedule an in-flight poll after stop, and stops on terminal state', async () => {
    vi.useFakeTimers()
    let finish: (v: boolean) => void = () => {}
    const work = vi.fn(
        () =>
          new Promise<boolean>((resolve) => {
            finish = resolve
          }),
      ),
      p = createPoller(work, 1000)
    p.start()
    p.stop()
    finish(false)
    await Promise.resolve()
    expect(vi.getTimerCount()).toBe(0)
    const q = createPoller(async () => true, 1000)
    q.start()
    await Promise.resolve()
    expect(vi.getTimerCount()).toBe(0)
  })
  it('encodes credentials as a QR matrix without browser APIs', () => {
    const matrix = qrMatrix('credential.test.value')
    expect(matrix.length).toBeGreaterThanOrEqual(21)
    expect(matrix.every((row) => row.length === matrix.length)).toBe(true)
    expect(matrix[0]!.slice(0, 7)).toEqual([
      true,
      true,
      true,
      true,
      true,
      true,
      true,
    ])
  })
  it('keeps delivery failure and queue completion distinct', () => {
    expect(deliveryStatuses.SENT).toBe('已送达')
    expect(deliveryStatuses.DEAD).toContain('失败')
    expect(queueStatuses.MISSED).toBe('已过号')
    expect(queueStatuses.COMPLETED).toBe('已完成')
  })
})
