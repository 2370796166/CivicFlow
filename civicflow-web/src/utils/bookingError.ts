const messages: Record<string, string> = {
  APPT_409_STOCK_EMPTY: '该时段名额已约满，请选择其他时段。',
  APPT_409_SLOT_FULL: '该时段名额已约满，请选择其他时段。',
  APPT_409_DUP_ACTIVE: '同一事项当天已有预约，请到“我的预约”查看。',
  APPT_409_STATE_CONFLICT: '预约状态已变化，请刷新后核实。',
  APPT_409_CONFIRM_TIMEOUT: '预约确认时间已过，请重新选择号源。',
  APPT_422_SLOT_NOT_OPEN: '该时段暂不可预约，请刷新号源后重新选择。',
  APPT_410_RESERVATION_GONE: '本次预约已失效，请重新选择号源。',
  APPT_503_PUBLISH_FAILED: '本次预约未成功，名额已释放，请稍后重新预约。',
  APPT_503_PUBLISH_UNKNOWN: '预约仍在处理中，请继续查询本次结果。',
  APPT_503_COMPENSATION_PENDING: '本次预约未成功，正在恢复名额，请稍后再试。',
  SECURITY_410_QR_EXPIRED: '签到二维码已过期或已被刷新，请重新获取二维码。',
  QUEUE_422_CHECKIN_WINDOW: '当前不在可签到时段，或网点不匹配，请核对预约。',
  QUEUE_503_CHECKIN_PENDING: '签到正在处理中，请刷新预约状态后核实。',
}
export function bookingErrorMessage(code: string | null | undefined, fallback = '操作未完成，请刷新状态后重试。') {
  return code && messages[code] ? messages[code] : fallback
}
