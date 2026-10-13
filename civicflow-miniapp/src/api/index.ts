import { request, RequestError } from './client'
import type {
  Appointment,
  Credential,
  Item,
  Notification,
  Outlet,
  Page,
  Progress,
  QueueTicket,
  Reservation,
  Rules,
  Slot,
  Stock,
  User,
} from '@/types'
import { mergeStock } from '@/utils/flow'
const query = (params: Record<string, string | number | undefined>) =>
  '?' +
  Object.entries(params)
    .filter(([, v]) => v !== undefined)
    .map(([k, v]) => `${k}=${encodeURIComponent(v!)}`)
    .join('&')
export const api = {
  me: () => request<User>('/user/me'),
  outlets: (keyword = '', page = 1) =>
    request<Page<Outlet>>('/user/outlets' + query({ keyword, page, size: 20 })),
  outlet: (id: string) => request<Outlet>(`/user/outlets/${id}`),
  items: (id: string, page = 1) =>
    request<Page<Item>>(
      `/user/outlets/${id}/items` + query({ page, size: 20 }),
    ),
  rules: () => request<Rules>('/user/slots/rules'),
  async slots(outletId: string, itemId: string, date: string, page = 1) {
    const result = await request<Page<Slot>>(
      '/user/slots' +
        query({ outletId, itemId, serviceDate: date, page, size: 20 }),
    )
    let stock: Stock[] = []
    try {
      stock = await request<Stock[]>(
        '/user/slots/availability' + query({ outletId, itemId, date }),
      )
    } catch {
      /* unknown inventory remains explicit */
    }
    return { ...result, items: mergeStock(result.items, stock) }
  },
  async reserve(slotId: string, key: string) {
    try {
      return await request<Reservation>(
        '/user/appointments/reservations',
        'POST',
        { slotId },
        key,
      )
    } catch (error) {
      if (
        error instanceof RequestError &&
        error.status === 503 &&
        error.data &&
        typeof error.data === 'object' &&
        'reservationId' in error.data &&
        typeof error.data.reservationId === 'string'
      )
        return error.data as Reservation
      throw error
    }
  },
  reservation: (id: string) =>
    request<Reservation>(
      `/user/appointments/reservations/${encodeURIComponent(id)}`,
    ),
  appointments: (page = 1, status?: string) =>
    request<Page<Appointment>>(
      '/user/appointments' +
        query({ page, size: 20, status: status || undefined }),
    ),
  appointment: (id: string) => request<Appointment>(`/user/appointments/${id}`),
  confirm: (id: string, version: number, key: string) =>
    request<Appointment>(
      `/user/appointments/${id}/confirm`,
      'POST',
      { version },
      key,
    ),
  cancel: (id: string, version: number, key: string) =>
    request<Appointment>(
      `/user/appointments/${id}/cancel`,
      'POST',
      { version, reason: 'USER_REQUEST' },
      key,
    ),
  credential: (id: string, outletId: string) =>
    request<Credential>(`/user/appointments/${id}/check-in-token`, 'POST', {
      outletId,
    }),
  checkin: (outletId: string, checkInToken: string) =>
    request<QueueTicket>('/user/check-ins', 'POST', { outletId, checkInToken }),
  queue: (appointmentId?: string) =>
    request<Progress[]>(
      '/user/queue-tickets/current' + query({ appointmentId }),
    ),
  ticket: (id: string) => request<Progress>(`/user/queue-tickets/${id}`),
  notifications: (page = 1) =>
    request<Page<Notification>>(
      '/user/notifications' + query({ page, size: 20 }),
    ),
  read: (id: string) =>
    request<{ read: boolean }>(`/user/notifications/${id}/read`, 'POST'),
  preference: () =>
    request<{ optionalInAppEnabled: boolean }>(
      '/user/notification-preferences',
    ),
  savePreference: (optionalInAppEnabled: boolean) =>
    request<{ optionalInAppEnabled: boolean }>(
      '/user/notification-preferences',
      'PUT',
      { optionalInAppEnabled },
    ),
}
