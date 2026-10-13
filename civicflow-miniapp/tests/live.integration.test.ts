import { readFileSync } from 'node:fs'
import { describe, expect, it, vi } from 'vitest'
import { api } from '../src/api'
import { login, logout, restoreSession } from '../src/api/client'
import { useSession } from '../src/stores/session'
import { pinia } from '../src/stores/pinia'
import { operationKey } from '../src/utils/flow'
import { execFile } from 'node:child_process'
import { promisify } from 'node:util'
import { fileURLToPath } from 'node:url'
vi.mock('../src/config', () => ({
  API_BASE: 'http://127.0.0.1:18080/api/v1',
  REFRESH_MAX_AGE: 86400000,
}))
const fixturePath = process.env.CIVICFLOW_MINI_FIXTURE
describe.skipIf(!fixturePath)(
  'real gateway + MySQL + Redis + RabbitMQ closed loop',
  () => {
    it('creates, confirms, checks in, follows staff service, reads notifications and restores session', async () => {
      const f = JSON.parse(readFileSync(fixturePath!, 'utf8')) as {
        base: string
        password: string
        users: Record<string, { name: string }>
        outlet: { id: string }
        item: { id: string }
        window: { id: string }
        slot: { id: string; serviceDate: string }
      }
      const storage = new Map<string, unknown>()
      vi.stubGlobal('getCurrentPages', () => [{ route: 'pages/home/index' }])
      vi.stubGlobal('uni', {
        request: (o: {
          url: string
          method: string
          data?: object
          header: Record<string, string>
          success: (v: object) => void
          fail: () => void
        }) => {
          let url = o.url
          if (o.method === 'GET' && o.data)
            url += '?' + new URLSearchParams(o.data as Record<string, string>)
          void fetch(url, {
            method: o.method,
            headers: o.header,
            body: o.method === 'GET' ? undefined : JSON.stringify(o.data),
          })
            .then(async (r) =>
              o.success({
                statusCode: r.status,
                data: await r.json(),
                header: Object.fromEntries(r.headers),
              }),
            )
            .catch(o.fail)
        },
        setStorageSync: (k: string, v: unknown) => storage.set(k, v),
        getStorageSync: (k: string) => storage.get(k),
        removeStorageSync: (k: string) => storage.delete(k),
        navigateTo: vi.fn(),
      })
      await login(f.users.user!.name, f.password)
      expect((await api.me()).roles).toContain('USER')
      expect(
        (await api.outlets('小程序')).items.some((o) => o.id === f.outlet.id),
      ).toBe(true)
      expect(
        (await api.items(f.outlet.id)).items.some((i) => i.id === f.item.id),
      ).toBe(true)
      const slots = await api.slots(f.outlet.id, f.item.id, f.slot.serviceDate)
      expect(slots.items.find((s) => s.id === f.slot.id)?.remaining).toBe(5)
      expect((await api.rules()).advanceDays).toBeGreaterThan(0)
      const accepted = await api.reserve(f.slot.id, operationKey())
      const wait = async <T>(fn: () => Promise<T>, done: (v: T) => boolean) => {
        for (let n = 0; n < 45; n++) {
          const v = await fn()
          if (done(v)) return v
          await new Promise((r) => setTimeout(r, 1000))
        }
        throw new Error('Bounded eventual-consistency wait expired')
      }
      const result = await wait(
        () => api.reservation(accepted.reservationId),
        (r) => !!r.appointment,
      )
      const a = result.appointment!
      const confirmed = await api.confirm(
        a.appointmentId,
        a.version,
        operationKey(),
      )
      expect(confirmed.status).toBe('CONFIRMED')
      const credential = await api.credential(a.appointmentId, a.outletId)
      expect(Date.parse(credential.expiresAt)).toBeGreaterThan(Date.now())
      const ticket = await api.checkin(a.outletId, credential.token)
      expect((await api.ticket(ticket.id)).ticket.status).toBe('WAITING')
      // Employee actions are performed in the actual Web workbench UI.
      const observed = new Set<string>(['WAITING'])
      let webDone = false
      const staffUi = promisify(execFile)(
        process.execPath,
        [
          fileURLToPath(new URL('../tools/staff-web.mjs', import.meta.url)),
          fixturePath!,
          ticket.id,
        ],
        { timeout: 90000 },
      ).finally(() => {
        webDone = true
      })
      await Promise.all([
        staffUi,
        (async () => {
          while (!webDone) {
            observed.add((await api.ticket(ticket.id)).ticket.status)
            await new Promise((resolve) => setTimeout(resolve, 500))
          }
        })(),
      ])
      expect([...observed]).toContain('CALLED')
      expect([...observed]).toContain('SERVING')
      expect((await api.ticket(ticket.id)).ticket.status).toBe('COMPLETED')
      await wait(
        () => api.appointment(a.appointmentId),
        (order) => order.status === 'COMPLETED',
      )
      const notifications = await wait(
        () => api.notifications(),
        (list) =>
          [
            'APPOINTMENT_CREATED',
            'APPOINTMENT_CONFIRMED',
            'QUEUE_CALLED',
          ].every((code) =>
            list.items.some(
              (n) => n.templateCode === code && n.status === 'SENT',
            ),
          ),
      )
      expect(notifications.items.length).toBeGreaterThanOrEqual(3)
      const n = notifications.items.find((n) => n.status === 'SENT')!
      expect((await api.read(n.id)).read).toBe(true)
      expect(
        (await api.notifications()).items.find((x) => x.id === n.id)?.readAt,
      ).toBeTruthy()
      await api.savePreference(false)
      expect((await api.preference()).optionalInAppEnabled).toBe(false)
      await api.savePreference(true)
      expect((await api.preference()).optionalInAppEnabled).toBe(true)
      const cancelledRequest = await api.reserve(f.slot.id, operationKey())
      const cancellable = (
        await wait(
          () => api.reservation(cancelledRequest.reservationId),
          (r) => !!r.appointment,
        )
      ).appointment!
      expect(
        (
          await api.cancel(
            cancellable.appointmentId,
            cancellable.version,
            operationKey(),
          )
        ).status,
      ).toBe('CANCELLED')
      expect(
        (await api.appointments(1, 'CANCELLED')).items.some(
          (x) => x.appointmentId === cancellable.appointmentId,
        ),
      ).toBe(true)
      const session = useSession(pinia)
      session.accessToken = ''
      session.refreshToken = ''
      await restoreSession()
      expect(session.user?.roles).toContain('USER')
      await logout()
      expect(session.user).toBeNull()
    }, 180000)
  },
)
