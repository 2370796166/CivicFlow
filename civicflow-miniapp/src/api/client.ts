import { API_BASE } from '@/config'
import type { Envelope, TokenPair } from '@/types'
import { pinia } from '@/stores/pinia'
import { useSession } from '@/stores/session'
import { loginRequired } from '@/utils/navigation'
import { operationKey } from '@/utils/flow'
export class RequestError extends Error {
  constructor(
    message: string,
    public status = 0,
    public code = 'NETWORK',
    public data: unknown = null,
    public requestId = '',
  ) {
    super(message)
  }
}
type Method = 'GET' | 'POST' | 'PUT'
let refreshFlight: Promise<void> | null = null
async function raw<T>(
  path: string,
  method: Method,
  data?: object,
  token = '',
  key?: string,
): Promise<T> {
  return new Promise((resolve, reject) => {
    uni.request({
      url: API_BASE + path,
      method,
      data,
      timeout: 15000,
      header: {
        'Content-Type': 'application/json',
        'X-Request-Id': operationKey(),
        ...(token ? { Authorization: 'Bearer ' + token } : {}),
        ...(key ? { 'Idempotency-Key': key } : {}),
      },
      success(response) {
        const body = response.data as Envelope<T>
        if (
          response.statusCode >= 200 &&
          response.statusCode < 300 &&
          body &&
          body.code === 'OK'
        )
          resolve(body.data)
        else
          reject(
            new RequestError(
              body?.message ||
                (response.statusCode === 429
                  ? '操作较频繁，请稍后重试'
                  : '请求失败，请重试'),
              response.statusCode,
              body?.code || 'HTTP',
              body?.data,
              body?.requestId ||
                String(response.header?.['X-Request-Id'] || ''),
            ),
          )
      },
      fail() {
        reject(new RequestError('网络连接失败，请检查网络后重试'))
      },
    })
  })
}
export async function restoreSession() {
  const session = useSession(pinia)
  if (session.accessToken && session.expiresAt > Date.now() + 10000) return
  if (refreshFlight) return refreshFlight
  const token = session.refreshToken || session.storedRefresh(),
    epoch = session.epoch
  if (!token) throw new RequestError('请先登录', 401)
  const flight = (async () => {
    try {
      const pair = await raw<TokenPair>('/auth/refresh', 'POST', {
        refreshToken: token,
      })
      if (session.epoch !== epoch) throw new RequestError('会话已退出', 401)
      session.accept(pair)
    } catch (error) {
      if (session.epoch === epoch) session.clear()
      throw error
    }
  })()
  refreshFlight = flight
  try {
    await flight
  } finally {
    if (refreshFlight === flight) refreshFlight = null
  }
}
export async function login(loginName: string, password: string) {
  const session = useSession(pinia)
  session.clear()
  const epoch = session.epoch
  const pair = await raw<TokenPair>('/auth/login', 'POST', {
    loginName,
    password,
  })
  if (epoch !== session.epoch) throw new RequestError('会话已变更', 401)
  if (!pair.user.roles.includes('USER')) {
    await raw(
      '/auth/logout',
      'POST',
      { refreshToken: pair.refreshToken },
      pair.accessToken,
    ).catch(() => {})
  }
  session.accept(pair)
}
export async function logout() {
  const session = useSession(pinia),
    token = session.accessToken,
    refreshToken = session.refreshToken
  session.clear()
  if (refreshToken) await raw('/auth/logout', 'POST', { refreshToken }, token)
}
export async function request<T>(
  path: string,
  method: Method = 'GET',
  data?: object,
  key?: string,
): Promise<T> {
  const session = useSession(pinia)
  try {
    await restoreSession()
  } catch (error) {
    loginRequired()
    throw error
  }
  const epoch = session.epoch,
    sentToken = session.accessToken
  try {
    const value = await raw<T>(path, method, data, sentToken, key)
    if (epoch !== session.epoch)
      throw new RequestError('会话已变更，请重新登录', 401)
    return value
  } catch (error) {
    if (!(error instanceof RequestError)) throw error
    if (error.status === 401 && epoch === session.epoch) {
      try {
        if (session.accessToken === sentToken) {
          session.expiresAt = 0
          await restoreSession()
        }
        if (epoch !== session.epoch) throw error
        const value = await raw<T>(path, method, data, session.accessToken, key)
        if (epoch !== session.epoch)
          throw new RequestError('会话已变更，请重新登录', 401)
        return value
      } catch (retryError) {
        if (
          epoch === session.epoch &&
          retryError instanceof RequestError &&
          retryError.status === 401
        )
          session.clear()
        if (!session.accessToken) loginRequired()
        throw retryError
      }
    }
    // Only reads get one bounded transient retry. Writes are never retried here.
    if (method === 'GET' && [0, 429, 502, 503, 504].includes(error.status)) {
      await new Promise((resolve) =>
        setTimeout(resolve, error.status === 429 ? 1500 : 600),
      )
      if (epoch !== session.epoch) throw error
      const value = await raw<T>(path, method, data, session.accessToken)
      if (epoch !== session.epoch)
        throw new RequestError('会话已变更，请重新登录', 401)
      return value
    }
    throw error
  }
}
export function errorText(error: unknown) {
  if (error instanceof RequestError)
    return (
      (friendlyErrors[error.code] ||
        (error.status === 403
          ? '当前账号无权执行此操作，请联系服务人员'
          : error.message)) +
      (error.requestId ? `（查询码：${error.requestId}）` : '')
    )
  return error instanceof Error ? error.message : '操作未完成，请重试'
}
const friendlyErrors: Record<string, string> = {
  AUTH_401_UNAUTHORIZED: '账号或密码错误，或登录已失效，请重新登录',
  AUTH_401_REFRESH_REUSED: '登录已失效，请重新登录',
  COMMON_400_VALIDATION: '提交信息不符合要求，请检查后重试',
  COMMON_404_NOT_FOUND: '记录不存在或当前不可查看，请刷新后重试',
  COMMON_503_DEPENDENCY_UNAVAILABLE: '服务暂时不可用，请稍后重新查询',
  APPT_409_DUP_ACTIVE: '您已有有效预约，请先查看我的预约',
  APPT_409_STOCK_EMPTY: '此时段已约满，请选择其他时段',
  APPT_409_STATE_CONFLICT: '预约状态已变化，请刷新后查看',
  APPT_409_CONFIRM_TIMEOUT: '确认时间已过，请查询最新预约状态',
  APPT_410_RESERVATION_GONE: '此预约请求已失效，请查看我的预约',
  APPT_422_SLOT_NOT_OPEN: '此时段尚不可预约，请刷新或更换时段',
  APPT_503_PUBLISH_UNKNOWN: '预约结果尚未确定，请继续查询',
  APPT_503_PUBLISH_FAILED: '本次预约受理未完成，请查询最终结果',
  APPT_503_COMPENSATION_PENDING: '预约结果正在处理中，请继续查询',
  SECURITY_410_QR_EXPIRED: '签到凭证已失效，请重新获取',
  QUEUE_422_CHECKIN_WINDOW:
    '当前时间、日期或网点不符合签到要求，请核对预约安排',
}
