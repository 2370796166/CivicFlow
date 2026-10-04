import type { CurrentWorkSession, StaffAction } from '@/types/staff'

export const actionLabel: Record<StaffAction, string> = {
  start: '开始工作', end: '结束工作', 'call-next': '叫下一号', recall: '重呼当前号',
  'start-serving': '开始办理', miss: '标记过号', complete: '完成办理',
}
export const confirmText: Record<StaffAction, string> = {
  start: '确认在此窗口开始工作？', end: '确认结束本次窗口工作？结束后需要重新开工。',
  'call-next': '确认叫下一号？领取后会占用当前窗口。', recall: '确认重呼当前号码？',
  'start-serving': '确认开始办理当前号码？', miss: '确认将当前号码标记为过号？过号后无法在首版中重新入队。',
  complete: '确认当前号码已办理完成？完成后不能撤回。',
}
export function availableActions(current: CurrentWorkSession | null): StaffAction[] {
  if (!current) return ['start']
  if (current.session.status !== 'ACTIVE') return []
  if (!current.currentTicket) return ['call-next', 'end']
  if (current.currentTicket.status === 'CALLED') return ['recall', 'start-serving', 'miss']
  if (current.currentTicket.status === 'SERVING') return ['complete']
  return []
}
export function actionSignature(action: StaffAction, current: CurrentWorkSession | null, windowId: string): string {
  if (action === 'start') return `start:${windowId}`
  if (!current) return ''
  if (action === 'end') return `end:${current.session.id}:${current.session.version}`
  if (action === 'call-next') return `call:${current.session.id}`
  const ticket = current.currentTicket
  return ticket ? `${action}:${current.session.id}:${ticket.id}:${ticket.version}` : ''
}
