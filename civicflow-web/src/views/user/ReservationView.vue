<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import StatePanel from '@/components/StatePanel.vue'
import { userApi } from '@/api/user'
import { reservationTerminal } from '@/utils/userFlow'
import type { ReservationResult } from '@/types/user'

const route = useRoute()
const router = useRouter()
const result = ref<ReservationResult | null>(null)
const error = ref(false)
const stopped = ref(false)
let timer: ReturnType<typeof setTimeout> | undefined
let active = true
let attempts = 0
const maxAttempts = 45
async function poll() {
  clearTimeout(timer)
  if (!active) return
  try {
    const next = await userApi.reservation(String(route.params.reservationId))
    if (!active) return
    result.value = next; error.value = false
    if (next.appointment) { await router.replace(`/user/appointments/${next.appointment.appointmentId}`); return }
    if (reservationTerminal.has(next.status)) { stopped.value = true; return }
  } catch { error.value = true }
  attempts += 1
  if (attempts >= maxAttempts) { stopped.value = true; return }
  timer = setTimeout(() => { void poll() }, document.hidden ? 10000 : 2000)
}
function retry() { stopped.value = false; attempts = 0; void poll() }
onMounted(() => { active = true; void poll() })
onUnmounted(() => { active = false; clearTimeout(timer) })
</script>
<template>
  <div class="page-heading"><span class="eyebrow">普通用户 / 抢号结果</span><h1>正在核实预约</h1><p>抢号请求已受理后，订单可能仍在创建中。</p></div>
  <div class="content-card user-card">
    <StatePanel v-if="result?.status === 'FAILED'" state="error" title="本次预约未成功" :description="result.failureCode || '请重新查看号源。'" @retry="retry" />
    <template v-else-if="stopped"><div class="notice-box" role="status"><strong>暂时无法确认最终结果</strong><p>自动查询已停止。请稍后继续查询，或到“我的预约”查看；不要用新请求重复抢号。</p><div class="action-row"><el-button @click="retry">继续查询</el-button><RouterLink to="/user/appointments" class="link-text">我的预约</RouterLink></div></div></template>
    <StatePanel v-else-if="error" state="error" title="网络暂时不可用" description="将继续使用同一 reservationId 查询服务端结果。" @retry="retry" />
    <StatePanel v-else state="loading" title="预约处理中" />
  </div>
</template>
