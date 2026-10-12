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
export function checkInDateHint(serviceDate: string, now = Date.now()): string {
  const parts = new Intl.DateTimeFormat('en', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(new Date(now))
  const value = (type: string) => parts.find(part => part.type === type)?.value
  const today = `${value('year')}-${value('month')}-${value('day')}`
  if (serviceDate > today) return `预约日期为 ${serviceDate}，请在预约当天的可签到时段获取二维码。`
  if (serviceDate < today) return `预约日期 ${serviceDate} 已过，无法获取签到二维码。`
  return ''
}
