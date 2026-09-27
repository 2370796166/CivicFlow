export interface StaffItem { id: string; code: string; name: string }
export interface StaffScope {
  outletId: string; outletCode: string; outletName: string
  windowId: string; windowCode: string; windowName: string; items: StaffItem[]
}
export type StaffTicketStatus = 'WAITING' | 'CALLED' | 'MISSED' | 'SERVING' | 'COMPLETED'
export interface StaffTicket {
  id: string; appointmentId: string; ticketNo: string; status: StaffTicketStatus
  checkedInAt: string; calledWindowId: string | null; workSessionId: string | null
  callCount: number; version: number
}
export interface WorkSession {
  id: string; outletId: string; windowId: string; status: 'ACTIVE' | 'ENDED'
  version: number; startedAt: string; endedAt: string | null
}
export interface CurrentWorkSession { session: WorkSession; currentTicket: StaffTicket | null }
export type StaffAction = 'start' | 'end' | 'call-next' | 'recall' | 'start-serving' | 'miss' | 'complete'
