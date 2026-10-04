import axios from 'axios'
import type { ApiResponse, CurrentUser, LoginRequest, TokenPair } from '@/types/api'

// Auth requests use a separate client so a failed refresh cannot recursively refresh.
const authClient = axios.create({ baseURL: '/api/v1', timeout: 10000 })
export const authApi = {
  async login(payload: LoginRequest): Promise<TokenPair> {
    const { data } = await authClient.post<ApiResponse<TokenPair>>('/auth/login', payload)
    return data.data
  },
  async refresh(refreshToken: string): Promise<TokenPair> {
    const { data } = await authClient.post<ApiResponse<TokenPair>>('/auth/refresh', { refreshToken })
    return data.data
  },
  async logout(refreshToken: string, accessToken: string): Promise<void> {
    await authClient.post('/auth/logout', { refreshToken }, { headers: { Authorization: `Bearer ${accessToken}` } })
  },
  async me(accessToken: string): Promise<CurrentUser> {
    const { data } = await authClient.get<ApiResponse<CurrentUser>>('/user/me', { headers: { Authorization: `Bearer ${accessToken}` } })
    return data.data
  },
}
