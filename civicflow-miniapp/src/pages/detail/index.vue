<script setup lang="ts">
import { ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { api } from '@/api'
import { RequestError, errorText } from '@/api/client'
import { useLive } from '@/composables/live'
import {
  statuses,
  statusNotes,
  countdown,
  canCancel,
  instant,
  latestAppointment,
} from '@/utils/flow'
import { getOperation, clearOperation } from '@/utils/operations'
import { go } from '@/utils/navigation'
import type { Appointment, Rules } from '@/types'
import Feedback from '@/components/Feedback.vue'
let id = '',
  nextAt = 0
const a = ref<Appointment | null>(null),
  rules = ref<Rules | null>(null),
  error = ref(''),
  busy = ref(false),
  loading = ref(false),
  now = ref(Date.now())
onLoad((options) => {
  id = options?.id || ''
})
onShow(() => {
  nextAt = 0
})
async function refresh(current: () => boolean = () => true) {
  loading.value = !a.value
  try {
    const data = await api.appointment(id)
    if (!current()) return
    a.value = latestAppointment(a.value, data)
    error.value = ''
    if (!rules.value) {
      const r = await api.rules()
      if (current()) rules.value = r
    }
  } catch (e) {
    if (current()) error.value = errorText(e)
  } finally {
    if (current()) loading.value = false
  }
}
useLive(
  async (current) => {
    now.value = Date.now()
    if (now.value >= nextAt) {
      await refresh(current)
      nextAt = Date.now() + 5000
    }
    return (
      !!a.value &&
      ['COMPLETED', 'CANCELLED', 'EXPIRED', 'NO_SHOW'].includes(a.value.status)
    )
  },
  1000,
  () => {
    a.value = null
    rules.value = null
  },
)
async function act(action: 'confirm' | 'cancel') {
  if (!a.value || busy.value) return
  if (action === 'cancel') {
    const answer = await uni.showModal({
      title: '取消预约',
      content: '取消后需重新预约，确定取消本次预约吗？',
      confirmText: '确定取消',
    })
    if (!answer.confirm) return
  }
  const current = a.value
  const op = getOperation(id, action, current.version)
  busy.value = true
  error.value = ''
  try {
    a.value = latestAppointment(
      a.value,
      await api[action](id, op.version, op.key),
    )
    clearOperation(id, action)
  } catch (e) {
    error.value = errorText(e)
    if (e instanceof RequestError && e.status === 409) {
      clearOperation(id, action)
      await refresh()
      error.value = '预约状态已更新，请核对后再操作'
    } else if (e instanceof RequestError && e.status > 0 && e.status < 500)
      clearOperation(id, action)
  } finally {
    busy.value = false
  }
}
</script>
<template>
  <view class="page">
    <Feedback :loading="loading" :error="error" @retry="refresh()" />
    <template v-if="a">
      <view class="eyebrow">{{ statuses[a.status] }}</view
      ><view class="title">{{ a.itemName }}</view>
      <view class="card ticket"
        ><view class="name">{{ a.serviceDate }}</view
        ><view class="title"
          >{{ a.slotStartTime.slice(0, 5) }}–{{
            a.slotEndTime.slice(0, 5)
          }}</view
        ><view>{{ a.outletName }}</view
        ><view class="link" @tap="go('/pages/outlet/index?id=' + a.outletId)"
          >查看网点信息 →</view
        ></view
      >
      <view class="note">{{ statusNotes[a.status] }}</view>
      <view v-if="a.status === 'PENDING_CONFIRM'" class="card"
        ><view>确认截止：{{ instant(a.confirmDeadline) }}</view
        ><view class="name">剩余 {{ countdown(a.confirmDeadline, now) }}</view
        ><view class="muted">倒计时仅作提醒，预约状态以系统结果为准。</view
        ><button
          class="primary"
          :loading="busy"
          :disabled="busy"
          @tap="act('confirm')"
        >
          确认预约
        </button></view
      >
      <button
        v-if="a.status === 'CONFIRMED'"
        class="primary"
        @tap="go('/pages/checkin/index?id=' + id)"
      >
        获取签到码 / 自助签到
      </button>
      <button
        v-if="['CHECKED_IN', 'SERVING', 'COMPLETED'].includes(a.status)"
        class="primary"
        @tap="go('/pages/queue/index?appointmentId=' + id)"
      >
        查看排队进度
      </button>
      <button
        v-if="rules && canCancel(a, rules, now)"
        class="danger"
        :disabled="busy"
        :loading="busy"
        @tap="act('cancel')"
      >
        取消预约
      </button>
      <view
        v-else-if="['PENDING_CONFIRM', 'CONFIRMED'].includes(a.status)"
        class="muted"
        >取消规则尚未读取或已过可取消时间，请刷新后查看。</view
      >
      <view class="card muted"
        >预约编号：{{ a.appointmentId
        }}<view>创建时间：{{ instant(a.createdAt) }}</view
        ><view>更新时间：{{ instant(a.updatedAt) }}</view></view
      >
    </template>
  </view>
</template>
