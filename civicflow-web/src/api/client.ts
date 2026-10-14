import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus/es/components/message/index'
import 'element-plus/es/components/message/style/css.mjs'
import { pinia } from '@/stores/pinia'
import { useAuthStore } from '@/stores/auth'
import type { ApiError, ApiResponse } from '@/types/api'

declare module 'axios' { interface AxiosRequestConfig { dedupe?: boolean; _retried?: boolean; _authGeneration?: number; suppressErrorToast?: boolean } }

export const api = axios.create({ baseURL: '/api/v1', timeout: 15000 })
let refreshFlight: { generation: number; promise: Promise<void> } | null = null
let onUnauthorized: (() => void) | null = null
let onForbidden: (() => void) | null = null
const pendingGets = new Map<string, AbortController>()
export function setUnauthorizedHandler(handler: () => void) { onUnauthorized = handler }
export function setForbiddenHandler(handler: () => void) { onForbidden = handler }
function dedupeKey(config: InternalAxiosRequestConfig): string {
  return `${config.method}:${config.url}:${JSON.stringify(config.params ?? {})}`
}

api.interceptors.request.use((config) => {
  const auth = useAuthStore(pinia)
  config._authGeneration ??= auth.getSessionGeneration()
  if (config._authGeneration !== auth.getSessionGeneration()) throw new axios.CanceledError('Session changed')
  if (auth.accessToken) config.headers.Authorization = `Bearer ${auth.accessToken}`
  // Gateway replaces untrusted request IDs; its response ID is authoritative.
  config.headers['X-Request-Id'] = crypto.randomUUID()
  if (config.dedupe && config.method?.toLowerCase() === 'get' && !config.signal) {
    const key = dedupeKey(config)
    pendingGets.get(key)?.abort()
    const controller = new AbortController()
    config.signal = controller.signal
    pendingGets.set(key, controller)
  }
  return config
})

api.interceptors.response.use(
  (response) => {
    release(response.config)
    const generation = response.config._authGeneration
    if (generation !== undefined && generation !== useAuthStore(pinia).getSessionGeneration()) throw new axios.CanceledError('Session changed')
    return response
  },
  async (error: AxiosError<ApiResponse<unknown>>) => {
    const config = error.config
    if (config) release(config)
    if (axios.isCancel(error)) return Promise.reject(error)
    const auth = useAuthStore(pinia)
    const generation = auth.getSessionGeneration()
    if (config?._authGeneration !== undefined && config._authGeneration !== generation) return Promise.reject(normalize(error))
    if (error.response?.status === 401 && config && !config._retried && auth.accessToken) {
      config._retried = true
      try {
        // A different request may already have rotated the token before this 401 arrived.
        if (config.headers?.Authorization !== `Bearer ${auth.accessToken}`) return api(config)
        if (!refreshFlight || refreshFlight.generation !== generation) {
          const flight = { generation, promise: auth.refresh() }
          refreshFlight = flight
          void flight.promise.finally(() => { if (refreshFlight === flight) refreshFlight = null }).catch(() => {})
        }
        await refreshFlight.promise
        return api(config)
      } catch {
        if (auth.getSessionGeneration() === generation) { auth.clear(); onUnauthorized?.() }
        return Promise.reject(normalize(error))
      }
    }
    if (error.response?.status === 401) { auth.clear(); onUnauthorized?.() }
    const normalized = normalize(error)
    if (normalized.status === 403) onForbidden?.()
    else if (normalized.status !== 401 && !config?.suppressErrorToast) ElMessage.error(normalized.message)
    return Promise.reject(normalized)
  },
)
function release(config: InternalAxiosRequestConfig) {
  if (config.dedupe && config.method?.toLowerCase() === 'get') {
    const key = dedupeKey(config)
    if (pendingGets.get(key)?.signal === config.signal) pendingGets.delete(key)
  }
}
function normalize(error: AxiosError<ApiResponse<unknown>>): ApiError {
  const result = new Error(error.response?.data?.message || (error.response ? '请求失败，请稍后重试' : '网络连接失败，请检查网络')) as ApiError
  result.status = error.response?.status
  result.code = error.response?.data?.code
  result.requestId = error.response?.data?.requestId || error.response?.headers?.['x-request-id']
  result.data = error.response?.data?.data
  return result
}
