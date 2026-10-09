import { api } from './client'
import type { ApiError, ApiResponse, Page } from '@/types/api'
import type { Appointment, CheckInToken, Outlet, QueueProgress, QueueTicket, ReservationCreate, ReservationResult, ServiceItem, UserSlot } from '@/types/user'

async function body<T>(request: Promise<{ data: ApiResponse<T> }>): Promise<T> { return (await request).data.data }

export const userApi = {
  outlets(keyword = '', page = 1) { return body(api.get<ApiResponse<Page<Outlet>>>('/user/outlets', { params: { keyword, page } })) },
  outlet(id: string) { return body(api.get<ApiResponse<Outlet>>(`/user/outlets/${id}`)) },
  items(outletId: string, page = 1) { return body(api.get<ApiResponse<Page<ServiceItem>>>(`/user/outlets/${outletId}/items`, { params: { page } })) },
  slots(outletId: string, itemId: string, serviceDate: string, page = 1) { return body(api.get<ApiResponse<Page<UserSlot>>>('/user/slots', { params: { outletId, itemId, serviceDate, page }, suppressErrorToast: true })) },
  async reserve(slotId: string, key: string): Promise<ReservationCreate> {
    try { return await body(api.post<ApiResponse<ReservationCreate>>('/user/appointments/reservations', { slotId }, { headers: { 'Idempotency-Key': key } })) }
    catch (error) {
      // A 503 may still include a stable reservationId. Its projection must be queried.
      const failure = error as ApiError
      if (failure.status === 503 && typeof failure.data === 'object' && failure.data !== null && 'reservationId' in failure.data && typeof failure.data.reservationId === 'string') return failure.data as ReservationCreate
      throw error
    }
  },
  reservation(id: string) { return body(api.get<ApiResponse<ReservationResult>>(`/user/appointments/reservations/${id}`)) },
  appointments(page = 1, status?: string) { return body(api.get<ApiResponse<Page<Appointment>>>('/user/appointments', { params: { page, status } })) },
  appointment(id: string) { return body(api.get<ApiResponse<Appointment>>(`/user/appointments/${id}`)) },
  confirm(id: string, version: number, key: string) { return body(api.post<ApiResponse<Appointment>>(`/user/appointments/${id}/confirm`, { version }, { headers: { 'Idempotency-Key': key } })) },
  cancel(id: string, version: number, key: string) { return body(api.post<ApiResponse<Appointment>>(`/user/appointments/${id}/cancel`, { reason: 'USER_REQUEST', version }, { headers: { 'Idempotency-Key': key } })) },
  checkInToken(id: string, outletId: string) { return body(api.post<ApiResponse<CheckInToken>>(`/user/appointments/${id}/check-in-token`, { outletId })) },
  checkIn(outletId: string, checkInToken: string) { return body(api.post<ApiResponse<QueueTicket>>('/user/check-ins', { outletId, checkInToken }, { suppressErrorToast: true })) },
  queue(appointmentId: string) { return body(api.get<ApiResponse<QueueProgress[]>>('/user/queue-tickets/current', { params: { appointmentId } })) },
}
