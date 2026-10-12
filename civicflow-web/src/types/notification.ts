export interface Notification {
  id: string
  eventId: string
  templateCode: string
  templateVersion: number
  title: string
  body: string
  status: string
  createdAt: string
  sentAt: string | null
  readAt: string | null
}
