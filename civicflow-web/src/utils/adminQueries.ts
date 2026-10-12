import type { AdminSlot, LogSource } from '@/types/admin'
import { appointmentStatusText, queueStatusText } from './userFlow'

export function validId(value: string): boolean {
  return /^[1-9][0-9]*$/.test(value) && value.length <= 19 && BigInt(value) <= 9223372036854775807n
}
export function validateQuery(filters: Record<string, string>): string {
  for (const [key, value] of Object.entries(filters)) {
    if (key.endsWith('Id') && value && !(key === 'actorId' && value.trim() === '0') && !validId(value.trim())) return 'ID 必须是有效的正整数，请完整复制，勿输入网点或事项名称。'
  }
  if (filters.dateFrom && filters.dateTo && filters.dateFrom > filters.dateTo) return '开始日期不能晚于结束日期。'
  return ''
}
export function queryValues<T extends Record<string, string>>(filters: T): Partial<T> {
  return Object.fromEntries(Object.entries(filters).map(([key, value]) => [key, value.trim() || undefined])) as Partial<T>
}
export function adminTime(value: string | null | undefined): string {
  if (!value) return '—'
  return new Intl.DateTimeFormat('zh-CN', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false }).format(new Date(value))
}
const labels: Record<string, string> = {
  ...appointmentStatusText, ...queueStatusText,
  RUNNING: '执行中', FAILED: '失败', SUCCESS: '成功', SUCCEEDED: '成功', ADMIN: '管理员', USER: '用户', STAFF: '窗口人员', SYSTEM: '系统', SERVICE: '服务', ANONYMOUS: '未登录',
  SCHEDULED: '已排期', OPEN: '开放', SUSPENDED: '暂停', CLOSED: '关闭', DRAFT: '草稿',
  CONSISTENT: '库存一致', REDIS_STOCK_MISMATCH: '库存差异', EVIDENCE_MISSING: '证据不完整', CONFIG_VERSION_GAP: '配置未同步', TRANSIENT_PENDING: '变更同步中', DB_ORDER_REDIS_ACTIVE_MISSING: '预约占位缺失', COMPENSATION_PENDING: '等待补偿', UNRESOLVED_RESERVATION: '预占未完成',
  NOT_REQUIRED: '无需修复', PENDING: '待处理', REPAIRED: '已修复', MANUAL_REQUIRED: '需人工核查', NOT_ATTEMPTED: '未执行修复',
  CREATE: '创建', UPDATE: '修改', DELETE: '删除', CONFIRM: '确认预约', CANCEL: '取消预约', EXPIRE: '预约超时', CHECK_IN: '签到', START_SERVING: '开始办理', COMPLETE: '完成办理', NO_SHOW: '过号',
  LOGIN: '登录', LOGIN_SUCCESS: '登录成功', LOGIN_FAILURE: '登录失败', LOGOUT: '退出登录', REFRESH: '刷新会话', USER_CREATE: '创建用户', USER_STATUS_CHANGE: '变更用户状态', USER_ROLES_CHANGE: '变更用户角色',
  OUTLET: '网点', ITEM: '事项', WINDOW: '窗口', SLOT: '号源', APPOINTMENT: '预约', QUEUE_TICKET: '排队票',
  CREATE_OUTLET: '新增网点', UPDATE_OUTLET: '修改网点', CHANGE_OUTLET_STATUS: '变更网点状态', DELETE_OUTLET: '删除网点',
  CREATE_ITEM: '新增事项', UPDATE_ITEM: '修改事项', CHANGE_ITEM_STATUS: '变更事项状态', DELETE_ITEM: '删除事项',
  CREATE_WINDOW: '新增窗口', UPDATE_WINDOW: '修改窗口', CHANGE_WINDOW_STATUS: '变更窗口状态', DELETE_WINDOW: '删除窗口', REPLACE_WINDOW_ITEMS: '绑定窗口事项',
  CREATE_SLOT: '新增号源', BATCH_CREATE_SLOTS: '批量生成号源', UPDATE_SLOT: '修改号源', CHANGE_SLOT_STATUS: '变更号源状态', ADJUST_SLOT_QUOTA: '调整号源额度',
  CREATE_USER: '创建账号', CHANGE_USER_STATUS: '变更账号状态', REPLACE_USER_ROLES: '变更账号角色', REFRESH_TOKEN_REUSE: '拦截重复会话令牌',
  START_SESSION: '开始窗口值班', END_SESSION: '结束窗口值班', CALL: '叫号', RECALL: '重呼', MARK_MISSED: '标记过号', MARK_NO_SHOW: '标记过号', LINK_QUEUE_TICKET: '关联排队票',
  PREHEAT_SLOT: '初始化号源库存', RECONCILIATION_REPAIR: '修复库存差异', ATTEMPTED: '已发起', APPLIED: '已执行', STALE_RECOMPUTE: '状态变化，重新核查', BLOCKED: '已拦截', INITIALIZED: '初始化完成', ALREADY_INITIALIZED: '已初始化',
}
export const logActions: Record<LogSource, string[]> = {
  appointment: ['CREATE', 'CONFIRM', 'CANCEL', 'EXPIRE', 'CHECK_IN', 'LINK_QUEUE_TICKET', 'START_SERVING', 'COMPLETE', 'MARK_NO_SHOW'],
  queue: ['START_SESSION', 'END_SESSION', 'CHECK_IN', 'CALL', 'RECALL', 'START_SERVING', 'MARK_MISSED', 'COMPLETE'],
  resource: ['CREATE_OUTLET', 'UPDATE_OUTLET', 'CHANGE_OUTLET_STATUS', 'DELETE_OUTLET', 'CREATE_ITEM', 'UPDATE_ITEM', 'CHANGE_ITEM_STATUS', 'DELETE_ITEM', 'CREATE_WINDOW', 'UPDATE_WINDOW', 'CHANGE_WINDOW_STATUS', 'DELETE_WINDOW', 'REPLACE_WINDOW_ITEMS', 'CREATE_SLOT', 'BATCH_CREATE_SLOTS', 'UPDATE_SLOT', 'CHANGE_SLOT_STATUS', 'ADJUST_SLOT_QUOTA'],
  auth: ['CREATE_USER', 'CHANGE_USER_STATUS', 'REPLACE_USER_ROLES', 'REFRESH_TOKEN_REUSE'],
  stock: ['PREHEAT_SLOT', 'RECONCILIATION_REPAIR'],
}
export function adminLabel(value: string | null | undefined): string { return value ? labels[value] || value : '—' }
export function slotIssue(slot: AdminSlot | undefined, now = Date.now()): string {
  if (!slot) return ''
  if (Date.parse(slot.releaseAt) > now) return '该号源尚未放号，放号后可进行对账。'
  if (Date.parse(`${slot.serviceDate}T${slot.endTime}+08:00`) <= now) return '该号源的服务时段已结束，可查看历史报告。'
  if (!['SCHEDULED', 'OPEN', 'SUSPENDED'].includes(slot.status)) return '该号源当前状态不支持对账。'
  return ''
}
