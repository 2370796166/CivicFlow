<script setup lang="ts">
import { ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { api } from '@/api'
import { errorText, restoreSession } from '@/api/client'
import { useLive } from '@/composables/live'
import { pending, savePending, clearPending } from '@/utils/pending'
import { go, loginRequired } from '@/utils/navigation'
import type { Reservation } from '@/types'
const result = ref<Reservation | null>(null),
  error = ref(''),
  paused = ref(false),
  loading = ref(false),
  needsRecovery = ref(false)
let id = '',
  attempts = 0,
  nextAt = 0
onLoad((options) => {
  id = options?.id || ''
})
onShow(() => {
  nextAt = 0
  loading.value = false
})
async function poll(current: () => boolean) {
  try {
    await restoreSession()
  } catch (e) {
    if (current()) {
      error.value = errorText(e)
      paused.value = true
      loginRequired()
    }
    return true
  }
  if (!current()) return
  const saved = pending()
  id ||= saved?.reservationId || ''
  if (!id) {
    needsRecovery.value = !!saved
    paused.value = true
    return true
  }
  if (Date.now() < nextAt) return
  if (++attempts > 30) {
    paused.value = true
    return true
  }
  loading.value = true
  try {
    const data = await api.reservation(id)
    if (!current()) return
    result.value = data
    error.value = ''
    nextAt =
      Date.now() + Math.max(1000, Math.min(10000, saved?.pollAfterMs || 2000))
    if (data.appointment) {
      clearPending()
      go('/pages/detail/index?id=' + data.appointment.appointmentId, true)
      return true
    }
    if (data.status === 'FAILED') {
      clearPending()
      return true
    }
  } catch (e) {
    if (current()) {
      error.value = errorText(e)
      nextAt = Date.now() + 5000
    }
  } finally {
    if (current()) loading.value = false
  }
}
const live = useLive(poll, 1000, () => {
  result.value = null
  id = ''
  needsRecovery.value = false
})
function resume() {
  attempts = 0
  nextAt = 0
  paused.value = false
  live.start()
}
async function recoverWrite() {
  if (loading.value) return
  const saved = pending()
  if (!saved) {
    error.value = '没有可恢复的预约请求，请查看我的预约'
    return
  }
  loading.value = true
  try {
    const data = await api.reserve(saved.slotId, saved.key)
    savePending({
      ...saved,
      reservationId: data.reservationId,
      pollAfterMs: data.pollAfterMs,
    })
    id = data.reservationId
    needsRecovery.value = false
    resume()
  } catch (e) {
    error.value = errorText(e)
  } finally {
    loading.value = false
  }
}
</script>
<template>
  <view class="page">
    <view class="eyebrow">预约进度</view
    ><view class="title">{{
      result?.status === 'FAILED'
        ? '预约未成功'
        : paused
          ? '预约结果待查询'
          : '预约处理中'
    }}</view>
    <view class="card ticket"
      ><view>{{
        result?.status === 'FAILED'
          ? '系统未能完成本次预约。请重新选择时段。'
          : '正在核实受理及建单结果。网络异常不会自动重复预约。'
      }}</view
      ><view v-if="result?.failureCode" class="muted"
        >结果代码：{{ result.failureCode }}</view
      ></view
    >
    <view v-if="error" class="error">{{ error }}</view
    ><view v-if="paused" class="note"
      >暂时无法确定结果，请继续查询或到我的预约查看。</view
    >
    <button
      v-if="needsRecovery"
      :loading="loading"
      :disabled="loading"
      class="primary"
      @tap="recoverWrite"
    >
      核实原预约请求
    </button>
    <button
      v-else-if="result?.status !== 'FAILED'"
      class="primary"
      :disabled="loading"
      @tap="resume"
    >
      继续查询
    </button>
    <button @tap="go('/pages/appointments/index')">查看我的预约</button>
    <button v-if="result?.status === 'FAILED'" @tap="go('/pages/home/index')">
      重新选择时段
    </button>
  </view>
</template>
