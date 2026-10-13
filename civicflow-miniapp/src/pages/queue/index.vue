<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { api } from '@/api'
import { errorText } from '@/api/client'
import { useLive } from '@/composables/live'
import { queueStatuses, queueEstimate } from '@/utils/flow'
import Feedback from '@/components/Feedback.vue'
import type { Progress } from '@/types'
let ticketId = '',
  appointmentId = ''
const progress = ref<Progress | null>(null),
  error = ref(''),
  loading = ref(false)
onLoad((options) => {
  ticketId = options?.ticketId || ''
  appointmentId = options?.appointmentId || ''
})
const live = useLive(
  async (current) => {
    loading.value = !progress.value
    try {
      const data = ticketId
        ? await api.ticket(ticketId)
        : (await api.queue(appointmentId))[0] || null
      if (!current()) return
      progress.value = data
      error.value = ''
      return !!data && ['COMPLETED', 'MISSED'].includes(data.ticket.status)
    } catch (e) {
      if (current()) error.value = errorText(e)
    } finally {
      if (current()) loading.value = false
    }
  },
  4000,
  () => {
    progress.value = null
  },
)
</script>
<template>
  <view class="page">
    <view class="eyebrow">现场办理</view><view class="title">排队进度</view
    ><Feedback
      :loading="loading"
      :error="error"
      :empty="!progress"
      empty-text="暂无排队记录，请完成预约签到后查看"
      @retry="live.start"
    />
    <template v-if="progress">
      <view class="card center ticket"
        ><view class="muted">您的排队号</view
        ><view class="big-number">{{ progress.ticket.ticketNo }}</view
        ><view class="name">{{
          queueStatuses[progress.ticket.status] || progress.ticket.status
        }}</view></view
      >
      <view class="card"
        ><view class="row"
          ><text>前方人数</text
          ><text class="name">{{ progress.aheadCount }} 人</text></view
        ><view class="divider" /><view class="row"
          ><text>当前叫号</text
          ><text>{{ progress.currentCall || '暂无' }}</text></view
        ><view v-if="progress.ticket.calledWindowId" class="row"
          ><text>叫号窗口编号</text
          ><text>{{ progress.ticket.calledWindowId }}</text></view
        ><view v-if="progress.estimate" class="note">{{
          queueEstimate(progress.estimate)
        }}</view></view
      >
      <view v-if="progress.ticket.status === 'CALLED'" class="note"
        >已叫到您的号码，请留意现场窗口提示。</view
      ><view v-if="progress.ticket.status === 'MISSED'" class="note"
        >本次已过号，请联系现场服务人员。</view
      ><view v-if="progress.ticket.status === 'COMPLETED'" class="note"
        >本次办理已完成。</view
      > </template
    ><button @tap="live.start">刷新进度</button>
  </view>
</template>
