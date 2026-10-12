import type { ApiError } from '@/types/api'
export function adminErrorMessage(error: unknown): string {
  const value = error as ApiError
  switch (value.code) {
    case 'APPT_422_RECONCILIATION_NOT_RELEASED': return '该号源尚未放号，请在放号后再进行对账。'
    case 'APPT_422_RECONCILIATION_CLOSED': return '该号源的服务时段已结束，不能重新对账。可在下方查看历史报告。'
    case 'APPT_422_RECONCILIATION_STATUS': return '当前号源状态不支持对账，请选择已排期、开放或暂停的号源。'
    case 'COMMON_400_VALIDATION': return '查询条件不正确，请检查 ID、日期范围和分页参数。'
    case 'COMMON_404_NOT_FOUND': return '该记录不存在或已不可查询，请刷新列表。'
    case 'COMMON_503_DEPENDENCY_UNAVAILABLE': return '服务暂时不可用，请稍后重试。'
    case 'RESOURCE_409_VERSION_CONFLICT': case 'AUTH_409_VERSION_CONFLICT': return '数据已被其他人修改，已刷新列表。请重新打开后再操作。'
    case 'RESOURCE_409_CODE_EXISTS': case 'AUTH_409_USER_EXISTS': return '编码、用户名或手机号已被使用，请修改后重试。'
    case 'RESOURCE_409_IN_USE': return '已有未来号源或业务引用，当前不能停用或删除。'
    case 'RESOURCE_409_SLOT_OVERLAP': return '该日期已有重叠时段，请调整时间。'
    case 'RESOURCE_409_SLOT_STATE_CONFLICT': return '号源状态已变化，或当前状态不允许此操作。'
    case 'RESOURCE_409_QUOTA_BELOW_CONSUMED': return '新额度低于已消费数量，请提高额度。'
    case 'RESOURCE_409_CONSUMPTION_UNKNOWN': return '消费数据尚未同步，暂不能降低额度。'
    case 'COMMON_409_IDEMPOTENCY_CONFLICT': return '重复请求的内容不一致，请刷新后重试。'
    default: return value.message || '操作失败，请刷新后重试。'
  }
}
