import { api } from './client'
import type { ApiResponse } from '@/types/api'
import type { CurrentWorkSession, StaffScope, StaffTicket, WorkSession } from '@/types/staff'
import type { QueueTicket } from '@/types/user'

async function body<T>(request: Promise<{ data: ApiResponse<T> }>): Promise<T> { return (await request).data.data }
const quiet = { suppressErrorToast: true }
function keyHeader(key: string) { return { 'Idempotency-Key': key } }

export const staffApi = {
  checkIn(outletId: string, checkInToken: string) { return body(api.post<ApiResponse<QueueTicket>>('/staff/check-ins', { outletId, checkInToken }, quiet)) },
  scopes() { return body(api.get<ApiResponse<StaffScope[]>>('/staff/scopes', quiet)) },
  current(windowId: string) { return body(api.get<ApiResponse<CurrentWorkSession | null>>('/staff/work-sessions/current', { params: { windowId }, ...quiet })) },
  start(windowId: string, key: string) { return body(api.post<ApiResponse<WorkSession>>('/staff/work-sessions', { windowId }, { headers: keyHeader(key), ...quiet })) },
  end(session: WorkSession, key: string) { return body(api.delete<ApiResponse<WorkSession>>(`/staff/work-sessions/${session.id}`, { data: { version: session.version }, headers: keyHeader(key), ...quiet })) },
  callNext(sessionId: string, key: string) { return body(api.post<ApiResponse<StaffTicket | null>>(`/staff/work-sessions/${sessionId}/call-next`, {}, { headers: keyHeader(key), ...quiet })) },
  change(action: 'recall' | 'miss' | 'start' | 'complete', ticket: StaffTicket, sessionId: string, key: string) {
    return body(api.post<ApiResponse<StaffTicket>>(`/staff/queue-tickets/${ticket.id}/${action}`, { sessionId, version: ticket.version }, { headers: keyHeader(key), ...quiet }))
  },
}
