import type {
  Appointment,
  AppointmentStatus,
  Rules,
  Slot,
  Stock,
} from '@/types'
export function operationKey() {
  return (
    'mini-' +
    Date.now().toString(36) +
    '-' +
    Math.random().toString(36).slice(2) +
    '-' +
    Math.random().toString(36).slice(2)
  )
}
export function latestAppointment(
  current: Appointment | null,
  incoming: Appointment,
) {
  return current &&
    current.appointmentId === incoming.appointmentId &&
    current.version > incoming.version
    ? current
    : incoming
}
export function beijingDate(now = Date.now()) {
  return new Date(now + 8 * 3600000).toISOString().slice(0, 10)
}
export function plusDays(date: string, days: number) {
  return new Date(Date.parse(date + 'T00:00:00Z') + days * 86400000)
    .toISOString()
    .slice(0, 10)
}
export function instant(value: string | null) {
  return value
    ? new Date(Date.parse(value) + 8 * 3600000)
        .toISOString()
        .slice(0, 16)
        .replace('T', ' ')
    : '—'
}
export function countdown(value: string | null, now = Date.now()) {
  const s = Math.max(
    0,
    Math.ceil(((value ? Date.parse(value) : now) - now) / 1000),
  )
  return `${Math.floor(s / 60)}分${s % 60}秒`
}
export function dateAllowed(date: string, rules: Rules, now = Date.now()) {
  return (
    date >= beijingDate(now) &&
    date <= plusDays(beijingDate(now), rules.advanceDays) &&
    !rules.closedDates.includes(date)
  )
}
export function mergeStock(slots: Slot[], stock: Stock[]) {
  const values = new Map(stock.map((x) => [x.configuration.slotId, x]))
  return slots.map((s) => ({
    ...s,
    remaining: values.get(s.id)?.remaining ?? null,
    liveAvailability: values.get(s.id)?.availability || 'UNKNOWN',
  }))
}
export function slotState(s: Slot, rules: Rules, now = Date.now()) {
  if (!dateAllowed(s.serviceDate, rules, now))
    return { label: '停办或超出预约日期', enabled: false }
  if (
    s.bookingStatus === 'SUSPENDED' ||
    !['OPEN', 'SCHEDULED'].includes(s.status)
  )
    return { label: '暂停预约', enabled: false }
  if (
    s.bookingStatus === 'CLOSED' ||
    s.availability === 'CLOSED' ||
    s.liveAvailability === 'CLOSED' ||
    Date.parse(s.closeAt) <= now
  )
    return { label: '已截止', enabled: false }
  if (
    s.bookingStatus === 'UPCOMING' ||
    s.availability === 'NOT_RELEASED' ||
    s.liveAvailability === 'NOT_RELEASED' ||
    Date.parse(s.releaseAt) > now
  )
    return { label: '未放号', enabled: false }
  if (!s.reservable) return { label: '暂不可预约', enabled: false }
  if (s.remaining === 0 || s.liveAvailability === 'FULL')
    return { label: '已约满', enabled: false }
  if (s.remaining === null || s.liveAvailability !== 'AVAILABLE')
    return { label: '余量待核实，可尝试预约', enabled: true }
  return { label: `可预约 · 剩余 ${s.remaining}`, enabled: true }
}
export const statuses: Record<AppointmentStatus, string> = {
  PENDING_CONFIRM: '待确认',
  CONFIRMED: '已确认',
  CHECKED_IN: '已签到',
  SERVING: '办理中',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  EXPIRED: '已过期',
  NO_SHOW: '未到场',
}
export const statusNotes: Record<AppointmentStatus, string> = {
  PENDING_CONFIRM: '请在确认截止前确认预约。',
  CONFIRMED: '请按预约时间到网点，在可签到时段获取凭证。',
  CHECKED_IN: '签到成功，请关注叫号进度。',
  SERVING: '正在办理，请配合窗口工作人员。',
  COMPLETED: '本次办理已完成。',
  CANCELLED: '预约已取消。',
  EXPIRED: '预约确认已超时。',
  NO_SHOW: '系统已记录本次未到场。',
}
export const queueStatuses: Record<string, string> = {
  WAITING: '等待叫号',
  CALLED: '请到窗口办理',
  SERVING: '正在办理',
  COMPLETED: '已完成',
  MISSED: '已过号',
}
export const deliveryStatuses: Record<string, string> = {
  PENDING: '待处理',
  PROCESSING: '处理中',
  SENT: '已送达',
  FAILED: '送达失败，等待重试',
  DEAD: '送达失败，需服务人员处理',
}
export function queueEstimate(value: string) {
  return value === 'WAITING_ORDER_ONLY'
    ? '系统提供排队顺序，请留意现场叫号。'
    : queueStatuses[value] || value
}
export function canCancel(a: Appointment, rules: Rules, now = Date.now()) {
  return (
    ['PENDING_CONFIRM', 'CONFIRMED'].includes(a.status) &&
    Date.parse(`${a.serviceDate}T${a.slotStartTime}+08:00`) - now >=
      rules.cancellationNoticeSeconds * 1000
  )
}
