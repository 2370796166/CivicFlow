<script setup lang="ts">
import { ref, getCurrentInstance, nextTick, onScopeDispose } from 'vue'
import { onLoad, onHide, onUnload } from '@dcloudio/uni-app'
import { api } from '@/api'
import { errorText } from '@/api/client'
import { useLive } from '@/composables/live'
import { onSessionClear } from '@/stores/session'
import { beijingDate, countdown } from '@/utils/flow'
import { drawQr } from '@/utils/qr'
import { createCredentialVault } from '@/utils/credential'
import { go } from '@/utils/navigation'
import type { Appointment } from '@/types'
const owner = getCurrentInstance()?.proxy
let id = '',
  nextOrderAt = 0
const vault = createCredentialVault(),
  credential = vault.value
const width = Math.min(280, uni.getSystemInfoSync().windowWidth - 80)
const a = ref<Appointment | null>(null),
  error = ref(''),
  busy = ref(false),
  now = ref(Date.now()),
  expired = ref(false)
onLoad((options) => {
  id = options?.id || ''
})
function clear() {
  vault.clear()
  nextOrderAt = 0
  if (owner) {
    const ctx = uni.createCanvasContext('checkin-qr', owner)
    ctx.clearRect(0, 0, width, width)
    ctx.draw()
  }
}
onHide(clear)
onUnload(clear)
const off = onSessionClear(() => {
  clear()
  a.value = null
})
onScopeDispose(off)
async function issue() {
  if (busy.value || !a.value || a.value.status !== 'CONFIRMED') return
  clear()
  expired.value = false
  busy.value = true
  error.value = ''
  const run = vault.generation()
  try {
    const token = await api.credential(id, a.value.outletId)
    if (!live.visible.value || !vault.accept(token, run)) return
    await nextTick()
    if (run === vault.generation() && owner && credential.value)
      drawQr(token.token, owner, width)
  } catch (e) {
    if (run === vault.generation()) error.value = errorText(e)
  } finally {
    busy.value = false
  }
}
const live = useLive(async (current) => {
  now.value = Date.now()
  if (vault.expire(now.value)) {
    clear()
    expired.value = true
  }
  if (now.value >= nextOrderAt) {
    try {
      const data = await api.appointment(id)
      if (!current()) return
      a.value = data
      nextOrderAt = Date.now() + 5000
      if (data.status !== 'CONFIRMED') {
        clear()
        return true
      }
      if (
        !credential.value &&
        !expired.value &&
        !error.value &&
        data.serviceDate === beijingDate()
      )
        await issue()
    } catch (e) {
      if (current()) {
        error.value = errorText(e)
        nextOrderAt = Date.now() + 5000
      }
    }
  }
}, 1000)
async function checkin() {
  if (
    busy.value ||
    !a.value ||
    !credential.value ||
    Date.parse(credential.value.expiresAt) <= Date.now()
  )
    return
  busy.value = true
  error.value = ''
  try {
    const ticket = await api.checkin(a.value.outletId, credential.value.token)
    clear()
    go('/pages/queue/index?ticketId=' + ticket.id, true)
  } catch (e) {
    error.value = errorText(e)
    clear()
    expired.value = true
  } finally {
    busy.value = false
  }
}
</script>
<template>
  <view class="page">
    <view class="eyebrow">到场办理</view><view class="title">签到凭证</view
    ><view class="subtitle">向工作人员出示二维码，或使用自助签到。</view>
    <view v-if="a" class="card"
      ><view class="name">{{ a.itemName }}</view
      ><view>{{ a.outletName }}</view
      ><view class="muted"
        >{{ a.serviceDate }} · {{ a.slotStartTime.slice(0, 5) }}–{{
          a.slotEndTime.slice(0, 5)
        }}</view
      ></view
    >
    <view v-if="a && a.status !== 'CONFIRMED'" class="note"
      >当前预约不适用签到凭证，请查看最新预约状态。</view
    >
    <view v-else-if="a && a.serviceDate !== beijingDate()" class="note"
      >请在预约当天的可签到时段获取凭证。</view
    >
    <view class="card center">
      <canvas
        v-if="credential"
        canvas-id="checkin-qr"
        id="checkin-qr"
        :style="{ width: width + 'px', height: width + 'px', margin: 'auto' }"
      />
      <view v-if="credential" class="muted"
        >有效期剩余 {{ countdown(credential.expiresAt, now) }}</view
      >
      <view v-else class="muted">{{
        expired ? '凭证已失效，请重新获取' : '暂未生成签到码'
      }}</view>
    </view>
    <view v-if="error" class="error">{{ error }}</view>
    <button
      v-if="a?.status === 'CONFIRMED'"
      :disabled="busy"
      :loading="busy"
      @tap="issue"
    >
      {{ credential ? '刷新签到码' : '获取签到码' }}
    </button>
    <button
      v-if="credential"
      class="primary"
      :disabled="busy"
      :loading="busy"
      @tap="checkin"
    >
      我已到场，立即签到
    </button>
    <button @tap="go('/pages/detail/index?id=' + id, true)">
      查看预约状态
    </button>
    <view class="note"
      >签到时间、网点与预约状态由系统校验。自助签到成功后进入排队；请勿转发签到凭证。</view
    >
  </view>
</template>
