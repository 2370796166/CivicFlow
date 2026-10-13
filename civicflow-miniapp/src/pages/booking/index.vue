<script setup lang="ts">
import { ref, computed } from 'vue'
import { onLoad, onShow, onHide } from '@dcloudio/uni-app'
import { api } from '@/api'
import { errorText, RequestError } from '@/api/client'
import { usePaged } from '@/composables/page'
import { useLive } from '@/composables/live'
import {
  beijingDate,
  plusDays,
  dateAllowed,
  slotState,
  instant,
  countdown,
  operationKey,
} from '@/utils/flow'
import { pending, savePending } from '@/utils/pending'
import { go } from '@/utils/navigation'
import type { Rules, Slot } from '@/types'
import Feedback from '@/components/Feedback.vue'
import Pager from '@/components/Pager.vue'
const date = ref(beijingDate()),
  name = ref(''),
  rules = ref<Rules | null>(null),
  selected = ref<Slot | null>(null),
  now = ref(Date.now()),
  busy = ref(false),
  submitError = ref(''),
  ruleError = ref('')
let outletId = '',
  itemId = '',
  boundary = Infinity,
  nextAt = 0,
  refreshGeneration = 0
const { items, page, total, size, loading, error, load, invalidate } = usePaged(
  (p) => api.slots(outletId, itemId, date.value, p),
)
const end = computed(() =>
  plusDays(beijingDate(), rules.value?.advanceDays || 0),
)
onLoad((options) => {
  outletId = options?.outletId || ''
  itemId = options?.itemId || ''
  name.value = options?.name ? decodeURIComponent(options.name) : '办理事项'
})
onShow(() => {
  nextAt = 0
})
onHide(() => {
  refreshGeneration++
})
async function refresh() {
  const run = ++refreshGeneration,
    requestedDate = date.value
  ruleError.value = ''
  try {
    const r = await api.rules()
    if (run !== refreshGeneration || requestedDate !== date.value) return
    rules.value = r
  } catch (e) {
    if (run === refreshGeneration) ruleError.value = errorText(e)
    nextAt = Date.now() + 5000
    boundary = Infinity
    return
  }
  if (!dateAllowed(date.value, rules.value)) {
    invalidate()
    items.value = []
    selected.value = null
    nextAt = Date.now() + 30000
    boundary = Infinity
    return
  }
  await load()
  if (run !== refreshGeneration || requestedDate !== date.value) return
  if (selected.value)
    selected.value =
      items.value.find((s) => s.id === selected.value?.id) || null
  boundary = Math.min(
    ...items.value
      .flatMap((s) => [Date.parse(s.releaseAt), Date.parse(s.closeAt)])
      .filter((t) => t > Date.now()),
  )
  nextAt = Date.now() + 30000
}
useLive(async (current) => {
  now.value = Date.now()
  if (now.value >= nextAt || now.value >= boundary) {
    if (current()) await refresh()
  }
}, 1000)
function changeDate(event: { detail: { value: string } }) {
  date.value = event.detail.value
  selected.value = null
  submitError.value = ''
  invalidate()
  items.value = []
  void refresh()
}
function state(s: Slot) {
  return rules.value
    ? slotState(s, rules.value, now.value)
    : { label: '正在读取预约规则', enabled: false }
}
async function reserve() {
  if (busy.value || !selected.value || !state(selected.value).enabled) return
  if (pending()) {
    go('/pages/reservation/index')
    return
  }
  const slotId = selected.value.id,
    key = operationKey()
  savePending({ slotId, key }) // Persist before the write: a timeout does not authorize another reservation.
  busy.value = true
  submitError.value = ''
  try {
    const result = await api.reserve(slotId, key)
    savePending({
      slotId,
      key,
      reservationId: result.reservationId,
      pollAfterMs: result.pollAfterMs,
    })
    go('/pages/reservation/index')
  } catch (e) {
    submitError.value = errorText(e)
    if (!(e instanceof RequestError) || e.status === 0 || e.status >= 500)
      go('/pages/reservation/index')
    else {
      uni.removeStorageSync('civicflow.pending.v1')
      await refresh()
    }
  } finally {
    busy.value = false
  }
}
</script>
<template>
  <view class="page">
    <view class="eyebrow">预约办理</view><view class="title">{{ name }}</view>
    <view class="card"
      ><view class="name">办理日期</view
      ><picker
        mode="date"
        :value="date"
        :start="beijingDate()"
        :end="end"
        @change="changeDate"
        ><view class="field row"
          ><text>{{ date }}</text
          ><text class="link">更换日期 ›</text></view
        ></picker
      ><view v-if="rules" class="muted"
        >可提前 {{ rules.advanceDays }} 天预约，停办日期不可选。</view
      ></view
    >
    <Feedback :error="ruleError" @retry="refresh" />
    <view v-if="rules && !dateAllowed(date, rules)" class="note"
      >此日期停办或不在可预约范围内，请更换日期。</view
    >
    <view class="row"
      ><view class="section-title">选择时段</view
      ><text class="link" @tap="refresh">刷新余量</text></view
    >
    <Feedback
      :loading="loading"
      :error="error"
      :empty="!items.length"
      empty-text="此日期暂无可预约时段"
      @retry="refresh"
    />
    <view
      v-for="slot in items"
      :key="slot.id"
      class="card slot"
      :class="{
        selected: selected?.id === slot.id,
        disabled: !state(slot).enabled,
      }"
      @tap="state(slot).enabled && (selected = slot)"
    >
      <view class="row"
        ><text class="name"
          >{{ slot.startTime.slice(0, 5) }}–{{ slot.endTime.slice(0, 5) }}</text
        ><text class="tag">{{ state(slot).label }}</text></view
      >
      <view class="muted">放号：{{ instant(slot.releaseAt) }}</view
      ><view class="muted">截止：{{ instant(slot.closeAt) }}</view>
      <view v-if="Date.parse(slot.releaseAt) > now" class="muted"
        >距离放号 {{ countdown(slot.releaseAt, now) }}</view
      >
    </view>
    <Pager
      :page="page"
      :total="total"
      :size="size"
      :loading="loading"
      @change="load"
    />
    <view v-if="submitError" class="error">{{ submitError }}</view
    ><button
      class="primary"
      :loading="busy"
      :disabled="busy || !selected || !state(selected).enabled"
      @tap="reserve"
    >
      提交预约
    </button>
    <view class="muted"
      >提交后需等待建单结果，并在规定时间内确认预约。余量未知时可尝试提交，结果以系统受理为准。</view
    >
  </view>
</template>
