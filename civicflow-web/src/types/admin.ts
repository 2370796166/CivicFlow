import type { Role } from './api'
import type { Appointment } from './user'
export interface AppointmentLog { id: string; appointmentId: string; reservationId: string; actorType: string; actorId: string | null; operation: string; fromStatus: string | null; toStatus: string | null; requestId: string; occurredAt: string }
export type ResourceKind = 'outlets' | 'items' | 'windows'
export type ResourceStatus = 'ENABLED' | 'DISABLED'
export type SlotStatus = 'DRAFT' | 'SCHEDULED' | 'OPEN' | 'SUSPENDED' | 'CLOSED'
export interface ResourceBase { id: string; code: string; name: string; status: ResourceStatus; version: number }
export interface AdminOutlet extends ResourceBase { address: string; longitude: number | null; latitude: number | null; maskedContactPhone: string | null }
export interface AdminItem extends ResourceBase { description: string | null; defaultDurationMinutes: number }
export interface AdminWindow extends ResourceBase { outletId: string }
export interface WindowStaff { windowId: string; version: number; staffUserIds: string[]; inheritedStaffUserIds: string[] }
export type AdminResource = AdminOutlet | AdminItem | AdminWindow
export interface AdminSlot { id: string; outletId: string; itemId: string; serviceDate: string; startTime: string; endTime: string; totalQuota: number; releaseAt: string; checkInStart: string; checkInEnd: string; status: SlotStatus; configVersion: number; consumedHint: number | null; version: number }
export interface SlotBatchResult { created: number; skipped: number; failed: { serviceDate: string; code: string; message: string }[] }
export interface AdminUser { id: string; username: string; maskedMobile: string | null; displayName: string; status: 'ENABLED' | 'DISABLED' | 'LOCKED'; roles: Role[]; version: number }
export interface ReconciliationReport { runId: string; slotId: string; expected: number; actual: number | null; diff: number | null; configuredTotal: number; persistedConsumed: number; pendingReserved: number; successfulCompensations: number; pendingCompensations: number; classification: string; detectedAt: string; autoRepairable: boolean; repairStatus: string }
export interface AdminAppointment { userId: string; queueTicketId: string | null; appointment: Appointment; confirmedAt: string | null; cancelledAt: string | null; expiredAt: string | null; checkedInAt: string | null; servingAt: string | null; completedAt: string | null; noShowAt: string | null }
export type LogSource = 'appointment' | 'queue' | 'resource' | 'auth' | 'stock'
export interface OperationLog { id: string; source: LogSource; resourceType: string; resourceId: string | null; actorType: string; actorId: string | null; action: string; outcome: string | null; fromStatus: string | null; toStatus: string | null; requestId: string; occurredAt: string }
export interface ReconciliationHistory { runId: string; slotId: string | null; triggerType: string; status: 'RUNNING' | 'COMPLETED' | 'FAILED'; startedAt: string; completedAt: string | null; actorId: string | null; failureCode: string | null; report: ReconciliationReport | null }
export interface AdminQuery { page: number; size?: number; dateFrom?: string; dateTo?: string }
export interface AppointmentQuery extends AdminQuery { keyword?: string; userId?: string; outletId?: string; itemId?: string; slotId?: string; status?: string }
export interface LogQuery extends AdminQuery { resourceId?: string; actorId?: string; action?: string; requestId?: string }
export interface ReconciliationQuery extends AdminQuery { slotId?: string; status?: string }
