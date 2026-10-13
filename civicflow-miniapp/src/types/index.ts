export interface Envelope<T> {
  code: string
  message: string
  data: T
  requestId: string
}
export interface Page<T> {
  items: T[]
  page: number
  size: number
  total: number
}
export interface User {
  id: string
  displayName: string
  roles: ('USER' | 'STAFF' | 'ADMIN')[]
}
export interface TokenPair {
  accessToken: string
  refreshToken: string
  expiresIn: number
  user: User
}
export interface Outlet {
  id: string
  name: string
  address: string
  longitude: number | null
  latitude: number | null
  maskedContactPhone: string | null
}
export interface Item {
  id: string
  name: string
  description: string | null
  defaultDurationMinutes: number
}
export interface Rules {
  advanceDays: number
  closedDates: string[]
  cancellationNoticeSeconds: number
}
export interface Slot {
  id: string
  outletId: string
  itemId: string
  serviceDate: string
  startTime: string
  endTime: string
  totalQuota: number
  releaseAt: string
  closeAt: string
  bookingStatus: string
  status: string
  availability: string
  reservable: boolean
  remaining: number | null
  liveAvailability: string
}
export interface Stock {
  configuration: { slotId: string }
  remaining: number | null
  availability: string
}
export type AppointmentStatus =
  | 'PENDING_CONFIRM'
  | 'CONFIRMED'
  | 'CANCELLED'
  | 'EXPIRED'
  | 'CHECKED_IN'
  | 'SERVING'
  | 'COMPLETED'
  | 'NO_SHOW'
export interface Appointment {
  appointmentId: string
  reservationId: string
  slotId: string
  outletId: string
  itemId: string
  outletName: string
  itemName: string
  serviceDate: string
  slotStartTime: string
  slotEndTime: string
  status: AppointmentStatus
  confirmDeadline: string | null
  version: number
  createdAt: string
  updatedAt: string
}
export interface Reservation {
  reservationId: string
  status: string
  pollAfterMs?: number
  appointment?: Appointment | null
  failureCode: string | null
}
export interface Credential {
  token: string
  expiresAt: string
}
export interface QueueTicket {
  id: string
  appointmentId: string
  ticketNo: string
  status: 'WAITING' | 'CALLED' | 'SERVING' | 'COMPLETED' | 'MISSED'
  calledWindowId: string | null
  version: number
}
export interface Progress {
  ticket: QueueTicket
  aheadCount: number
  currentCall: string | null
  estimate: string
}
export interface Notification {
  id: string
  eventId: string
  title: string
  body: string
  status: string
  createdAt: string
  sentAt: string | null
  readAt: string | null
  templateCode: string
  templateVersion: number
}
