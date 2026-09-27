import type { Role } from './api'
export type ResourceKind = 'outlets' | 'items' | 'windows'
export type ResourceStatus = 'ENABLED' | 'DISABLED'
export type SlotStatus = 'DRAFT' | 'SCHEDULED' | 'OPEN' | 'SUSPENDED' | 'CLOSED'
export interface ResourceBase { id: string; code: string; name: string; status: ResourceStatus; version: number }
export interface AdminOutlet extends ResourceBase { address: string; longitude: number | null; latitude: number | null; maskedContactPhone: string | null }
export interface AdminItem extends ResourceBase { description: string | null; defaultDurationMinutes: number }
export interface AdminWindow extends ResourceBase { outletId: string }
export type AdminResource = AdminOutlet | AdminItem | AdminWindow
export interface AdminSlot { id: string; outletId: string; itemId: string; serviceDate: string; startTime: string; endTime: string; totalQuota: number; releaseAt: string; checkInStart: string; checkInEnd: string; status: SlotStatus; configVersion: number; consumedHint: number | null; version: number }
export interface SlotBatchResult { created: number; skipped: number; failed: { serviceDate: string; code: string; message: string }[] }
export interface AdminUser { id: string; username: string; maskedMobile: string | null; displayName: string; status: 'ENABLED' | 'DISABLED' | 'LOCKED'; roles: Role[]; version: number }
export interface ReconciliationReport { runId: string; slotId: string; expected: number; actual: number | null; diff: number | null; configuredTotal: number; persistedConsumed: number; pendingReserved: number; successfulCompensations: number; pendingCompensations: number; classification: string; detectedAt: string; autoRepairable: boolean; repairStatus: string }
