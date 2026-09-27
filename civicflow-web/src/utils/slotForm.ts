export function chinaInstant(local: string): string { return new Date(`${local}:00+08:00`).toISOString() }
export function chinaLocal(instant: string): string { return new Date(instant).toLocaleString('sv-SE', { timeZone: 'Asia/Shanghai', hour12: false }).replace(' ', 'T').slice(0, 16) }
export function dayCount(start: string, end: string): number {
  const a = Date.parse(`${start}T00:00:00Z`), b = Date.parse(`${end}T00:00:00Z`)
  return Number.isFinite(a) && Number.isFinite(b) ? Math.round((b - a) / 86400000) + 1 : 0
}
export function validateSlotTimes(start: string, end: string, checkStart: string, checkEnd: string): string {
  if (!start || !end || !checkStart || !checkEnd) return '请填写完整的时段和签到时间。'
  if (!(checkStart <= start && start < end && start <= checkEnd && checkEnd <= end)) return '需满足签到开始 ≤ 时段开始 < 时段结束，且签到结束在时段内。'
  return ''
}
