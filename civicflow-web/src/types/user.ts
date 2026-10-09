export interface Outlet { id: string; code: string; name: string; address: string; maskedContactPhone: string | null; status: 'ENABLED' | 'DISABLED' }
export interface ServiceItem { id: string; code: string; name: string; description: string | null; defaultDurationMinutes: number; status: 'ENABLED' | 'DISABLED' }
export interface UserSlot {
  id: string; outletId: string; itemId: string; serviceDate: string; startTime: string; endTime: string
  totalQuota: number; releaseAt: string; closeAt: string; bookingStatus: 'BOOKABLE' | 'UPCOMING' | 'SUSPENDED' | 'CLOSED'
}

export type AppointmentStatus = 'PENDING_CONFIRM' | 'CONFIRMED' | 'CANCELLED' | 'EXPIRED' | 'CHECKED_IN' | 'SERVING' | 'COMPLETED' | 'NO_SHOW'
export type ReservationStatus = 'CREATING' | 'FAILED' | AppointmentStatus
export interface Appointment {
  appointmentId: string; reservationId: string; slotId: string; outletId: string; itemId: string
  outletName: string; itemName: string; serviceDate: string; slotStartTime: string; slotEndTime: string
  status: AppointmentStatus; confirmDeadline: string | null; version: number; createdAt: string; updatedAt: string
}
export interface ReservationCreate { reservationId: string; status: 'CREATING' | 'FAILED'; pollAfterMs: number; failureCode: string | null }
export interface ReservationResult { reservationId: string; status: ReservationStatus; appointment: Appointment | null; failureCode: string | null }
export interface CheckInToken { token: string; expiresAt: string }
export type QueueStatus = 'WAITING' | 'CALLED' | 'SERVING' | 'COMPLETED' | 'MISSED'
export interface QueueTicket { id: string; appointmentId: string; ticketNo: string; status: QueueStatus; checkedInAt: string; calledWindowId: string | null; workSessionId: string | null; callCount: number; version: number }
export interface QueueProgress { ticket: QueueTicket; aheadCount: number; currentCall: string | null; estimate: string }
