import { api } from './client'
import type { ApiResponse, Page } from '@/types/api'
import type { Notification } from '@/types/notification'

export const notificationApi = {
  async list(page = 1) {
    const response = await api.get<ApiResponse<Page<Notification>>>('/user/notifications', { params: { page } })
    return response.data.data
  },
  async read(id: string) {
    await api.post(`/user/notifications/${id}/read`)
  },
  async preference() {
    const response = await api.get<ApiResponse<{ optionalInAppEnabled: boolean }>>('/user/notification-preferences')
    return response.data.data.optionalInAppEnabled
  },
  async savePreference(enabled: boolean) {
    await api.put('/user/notification-preferences', { optionalInAppEnabled: enabled })
  },
}
