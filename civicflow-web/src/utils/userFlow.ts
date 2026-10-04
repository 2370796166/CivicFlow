import type { AppointmentStatus, ReservationStatus } from '@/types/user'

export const appointmentStatusText: Record<AppointmentStatus, string> = {
  PENDING_CONFIRM: '待确认', CONFIRMED: '已确认', CANCELLED: '已取消', EXPIRED: '已超时',
  CHECKED_IN: '已签到', SERVING: '办理中', COMPLETED: '已完成', NO_SHOW: '已过号',
}
export const reservationTerminal = new Set<ReservationStatus>(['FAILED', 'PENDING_CONFIRM', 'CONFIRMED', 'CANCELLED', 'EXPIRED', 'CHECKED_IN', 'SERVING', 'COMPLETED', 'NO_SHOW'])
export const queueTerminal = new Set(['COMPLETED', 'MISSED'])
export const queueStatusText: Record<string, string> = { WAITING: '等待叫号', CALLED: '已叫号', SERVING: '办理中', COMPLETED: '已完成', MISSED: '已过号' }
export function remaining(expiresAt: string | null, now = Date.now()): string {
  if (!expiresAt) return '—'
  const seconds = Math.max(0, Math.ceil((Date.parse(expiresAt) - now) / 1000))
  return `${String(Math.floor(seconds / 60)).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`
}
export function canCancel(status: AppointmentStatus): boolean { return status === 'PENDING_CONFIRM' || status === 'CONFIRMED' }
