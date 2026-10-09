import { api } from './client'
import type { AdminAppointment, AppointmentLog } from '@/types/admin'
import type { ApiResponse, Page } from '@/types/api'
import type { AdminItem, AdminOutlet, AdminResource, AdminSlot, AdminUser, AdminWindow, ReconciliationReport, ResourceKind, ResourceStatus, SlotBatchResult, SlotStatus, WindowStaff } from '@/types/admin'

async function body<T>(request: Promise<{ data: ApiResponse<T> }>): Promise<T> { return (await request).data.data }
const key = () => ({ 'Idempotency-Key': crypto.randomUUID() })
const quiet = { suppressErrorToast: true }
export const adminApi = {
  appointments(params: { page: number; size?: number; userId?: string; outletId?: string; serviceDate?: string; status?: string }) { return body(api.get<ApiResponse<Page<AdminAppointment>>>('/admin/appointments', { params, ...quiet })) },
  appointment(id: string) { return body(api.get<ApiResponse<AdminAppointment>>(`/admin/appointments/${id}`, quiet)) },
  appointmentLogs(params: { page: number; size?: number; appointmentId?: string; operation?: string }) { return body(api.get<ApiResponse<Page<AppointmentLog>>>('/admin/appointment-operation-logs', { params, ...quiet })) },
  resources(kind: ResourceKind, params: { page: number; size?: number; keyword?: string; status?: string; outletId?: string }) { return body(api.get<ApiResponse<Page<AdminResource>>>(`/admin/${kind}`, { params, ...quiet })) },
  resource(kind: ResourceKind, id: string) { return body(api.get<ApiResponse<AdminResource>>(`/admin/${kind}/${id}`, quiet)) },
  createResource(kind: ResourceKind, value: Record<string, unknown>) { return body(api.post<ApiResponse<AdminResource>>(`/admin/${kind}`, value, { headers: key(), ...quiet })) },
  updateResource(kind: ResourceKind, id: string, value: Record<string, unknown>) { return body(api.put<ApiResponse<AdminResource>>(`/admin/${kind}/${id}`, value, { headers: key(), ...quiet })) },
  resourceStatus(kind: ResourceKind, row: AdminResource, status: ResourceStatus) { return body(api.patch<ApiResponse<AdminResource>>(`/admin/${kind}/${row.id}/status`, { status, version: row.version }, { headers: key(), ...quiet })) },
  deleteResource(kind: ResourceKind, row: AdminResource) { return body(api.delete<ApiResponse<null>>(`/admin/${kind}/${row.id}`, { params: { version: row.version }, headers: key(), ...quiet })) },
  bindWindowItems(row: AdminWindow, itemIds: string[], idempotencyKey: string = crypto.randomUUID()) { return body(api.put<ApiResponse<{ windowId: string; version: number; itemIds: string[] }>>(`/admin/windows/${row.id}/items`, { itemIds, version: row.version }, { headers: { 'Idempotency-Key': idempotencyKey }, ...quiet })) },
  windowItems(id: string) { return body(api.get<ApiResponse<{ windowId: string; version: number; itemIds: string[] }>>(`/admin/windows/${id}/items`, quiet)) },
  windowStaff(id: string) { return body(api.get<ApiResponse<WindowStaff>>(`/admin/windows/${id}/staff`, quiet)) },
  bindWindowStaff(id: string, staffUserIds: string[], version: number, idempotencyKey: string) { return body(api.put<ApiResponse<WindowStaff>>(`/admin/windows/${id}/staff`, { staffUserIds, version }, { headers: { 'Idempotency-Key': idempotencyKey }, ...quiet })) },
  slots(params: { page: number; size?: number; outletId?: string; itemId?: string; dateFrom?: string; dateTo?: string; status?: string }) { return body(api.get<ApiResponse<Page<AdminSlot>>>('/admin/slots', { params, ...quiet })) },
  slot(id: string) { return body(api.get<ApiResponse<AdminSlot>>(`/admin/slots/${id}`, quiet)) },
  createSlot(value: Record<string, unknown>) { return body(api.post<ApiResponse<AdminSlot>>('/admin/slots', value, { headers: key(), ...quiet })) },
  updateSlot(id: string, value: Record<string, unknown>) { return body(api.put<ApiResponse<AdminSlot>>(`/admin/slots/${id}`, value, { headers: key(), ...quiet })) },
  batchSlots(value: Record<string, unknown>) { return body(api.post<ApiResponse<SlotBatchResult>>('/admin/slots/batch', value, { headers: key(), ...quiet })) },
  slotStatus(row: AdminSlot, status: SlotStatus) { return body(api.patch<ApiResponse<AdminSlot>>(`/admin/slots/${row.id}/status`, { status, version: row.version }, { headers: key(), ...quiet })) },
  slotQuota(row: AdminSlot, totalQuota: number) { return body(api.patch<ApiResponse<AdminSlot>>(`/admin/slots/${row.id}/quota`, { totalQuota, configVersion: row.configVersion }, { headers: key(), ...quiet })) },
  users(params: { page: number; size?: number; keyword?: string; status?: string }) { return body(api.get<ApiResponse<Page<AdminUser>>>('/admin/users', { params, ...quiet })) },
  createUser(value: Record<string, unknown>) { return body(api.post<ApiResponse<AdminUser>>('/admin/users', value, { headers: key(), ...quiet })) },
  userStatus(row: AdminUser, status: 'ENABLED' | 'DISABLED') { return body(api.patch<ApiResponse<AdminUser>>(`/admin/users/${row.id}/status`, { status, version: row.version }, { headers: key(), ...quiet })) },
  userRoles(row: AdminUser, roles: string[]) { return body(api.put<ApiResponse<AdminUser>>(`/admin/users/${row.id}/roles`, { roles, version: row.version }, { headers: key(), ...quiet })) },
  reconcile(slotId: string, repair: boolean) { return body(api.post<ApiResponse<ReconciliationReport>>('/admin/reconciliations', { slotId, repair }, { headers: key(), ...quiet })) },
}
export type { AdminItem, AdminOutlet, AdminWindow }
