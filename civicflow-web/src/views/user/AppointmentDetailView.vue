<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import QRCode from 'qrcode'
import StatePanel from '@/components/StatePanel.vue'
import { userApi } from '@/api/user'
import { appointmentStatusText, canCancel, queueStatusText, queueTerminal, remaining } from '@/utils/userFlow'
import type { Appointment, QueueProgress } from '@/types/user'
import { bookingErrorMessage } from '@/utils/bookingError'
import type { ApiError } from '@/types/api'

const route = useRoute()
const record = ref<Appointment | null>(null)
const progress = ref<QueueProgress | null>(null)
const qr = ref('')
const qrToken = ref(''), checkInNotice = ref('')
const qrExpires = ref('')
const now = ref(Date.now())
const loading = ref(false)
const error = ref(false)
const action = ref('')
const qrBusy = ref(false)
const actionKeys = new Map<string, { version: number; key: string }>()
let clock: ReturnType<typeof setInterval> | undefined
let queueTimer: ReturnType<typeof setTimeout> | undefined
let qrTimer: ReturnType<typeof setTimeout> | undefined
let stateTimer: ReturnType<typeof setTimeout> | undefined
let disposed = false
let expiryChecked = false
const id = computed(() => String(route.params.appointmentId ?? ''))
const countdown = computed(() => remaining(record.value?.confirmDeadline ?? null, now.value))
const qrCountdown = computed(() => remaining(qrExpires.value, now.value))

async function load() {
  if (!id.value) return
  const requestedId = id.value
  loading.value = true; error.value = false
  try {
    const next = await userApi.appointment(id.value)
    if (disposed || requestedId !== id.value) return
    record.value = next
    if (next.status === 'CHECKED_IN' || next.status === 'SERVING') scheduleQueue(0)
    else { clearTimeout(queueTimer); progress.value = null }
    if (next.status !== 'CONFIRMED') { qr.value = ''; qrToken.value = ''; clearTimeout(qrTimer) }
  } catch { error.value = true }
  finally { loading.value = false; scheduleState() }
}
function scheduleState() {
  clearTimeout(stateTimer)
  if (!disposed && record.value && ['PENDING_CONFIRM', 'CONFIRMED', 'CHECKED_IN', 'SERVING'].includes(record.value.status)) {
    stateTimer = setTimeout(() => { void load() }, document.hidden ? 15000 : pollMs)
  }
}
async function command(kind: 'confirm' | 'cancel') {
  const current = record.value
  if (!current || action.value) return
  action.value = kind
  const saved = actionKeys.get(kind)
  const key = saved?.version === current.version ? saved.key : crypto.randomUUID()
  actionKeys.set(kind, { version: current.version, key })
  try {
    record.value = kind === 'confirm'
      ? await userApi.confirm(current.appointmentId, current.version, key)
      : await userApi.cancel(current.appointmentId, current.version, key)
    actionKeys.delete(kind)
    qr.value = ''
    ElMessage.success(kind === 'confirm' ? '预约已确认' : '预约已取消')
  } catch {
    // A timeout or lost response may follow a committed command. Read server state first.
    await load()
    if (record.value?.version !== current.version) actionKeys.delete(kind)
  } finally { action.value = ''; scheduleState() }
}
async function issueQr() {
  if (!record.value || record.value.status !== 'CONFIRMED' || qrBusy.value || action.value === 'check-in' || document.hidden) return
  qrBusy.value = true
  const requestedId = record.value.appointmentId
  try {
    const result = await userApi.checkInToken(requestedId, record.value.outletId)
    if (disposed || requestedId !== id.value || record.value?.status !== 'CONFIRMED') return
    const image = await QRCode.toDataURL(result.token, { width: 260, margin: 2, errorCorrectionLevel: 'M' })
    if (disposed || requestedId !== id.value || record.value?.status !== 'CONFIRMED') return
    qrToken.value = result.token
    qr.value = image
    qrExpires.value = result.expiresAt
    clearTimeout(qrTimer)
    qrTimer = setTimeout(() => { qr.value = ''; void issueQr() }, Math.max(1000, Date.parse(result.expiresAt) - Date.now() - 20000))
  } catch { qr.value = ''; qrToken.value = ''; qrExpires.value = '' }
  finally { qrBusy.value = false }
}
async function checkIn() {
  if (!record.value || record.value.status !== 'CONFIRMED' || !qrToken.value || action.value || qrBusy.value) return
  action.value = 'check-in'; checkInNotice.value = ''; clearTimeout(qrTimer)
  try {
    const ticket = await userApi.checkIn(record.value.outletId, qrToken.value)
    checkInNotice.value = `签到成功，排队号 ${ticket.ticketNo}。`
    qr.value = ''; qrToken.value = ''
    await load()
  } catch (failure) {
    checkInNotice.value = bookingErrorMessage((failure as ApiError).code, '签到结果暂不确定，请刷新预约状态后核实。')
    await load()
  } finally {
    action.value = ''
    if (record.value?.status === 'CONFIRMED' && qrToken.value) qrTimer = setTimeout(() => { void issueQr() }, Math.max(1000, Date.parse(qrExpires.value) - Date.now() - 20000))
  }
}
async function loadQueue() {
  if (!record.value || disposed || !['CHECKED_IN', 'SERVING'].includes(record.value.status)) return
  try {
    const rows = await userApi.queue(record.value.appointmentId)
    progress.value = rows.find((row) => row.ticket.appointmentId === record.value?.appointmentId) ?? null
    if (progress.value && queueTerminal.has(progress.value.ticket.status)) { scheduleState(); return }
  } catch { /* The next poll can recover a transient failure. */ }
  scheduleQueue(document.hidden ? 15000 : pollMs)
}
const configured = Number(import.meta.env.VITE_QUEUE_POLL_MS)
const pollMs = Number.isFinite(configured) && configured >= 3000 && configured <= 5000 ? configured : 4000
function scheduleQueue(delay: number) { clearTimeout(queueTimer); queueTimer = setTimeout(() => { void loadQueue() }, delay) }
function visibilityChanged() {
  scheduleState()
  if (!document.hidden && record.value?.status === 'CONFIRMED' && (!qr.value || Date.parse(qrExpires.value) - Date.now() < 30000)) void issueQr()
  if (record.value && ['CHECKED_IN', 'SERVING'].includes(record.value.status)) scheduleQueue(document.hidden ? 15000 : 0)
}
watch(id, () => { record.value = null; progress.value = null; qr.value = ''; qrToken.value = ''; checkInNotice.value = ''; expiryChecked = false; actionKeys.clear(); void load() })
onMounted(() => { disposed = false; clock = setInterval(() => {
  now.value = Date.now()
  if (!expiryChecked && record.value?.status === 'PENDING_CONFIRM' && record.value.confirmDeadline && Date.parse(record.value.confirmDeadline) <= now.value) { expiryChecked = true; void load() }
}, 1000); document.addEventListener('visibilitychange', visibilityChanged); void load() })
onUnmounted(() => { disposed = true; clearInterval(clock); clearTimeout(queueTimer); clearTimeout(qrTimer); clearTimeout(stateTimer); document.removeEventListener('visibilitychange', visibilityChanged) })
</script>

<template>
  <div class="page-heading">
    <span class="eyebrow">普通用户 / 我的预约</span><h1>预约详情</h1><p>预约和排队状态以服务端返回为准。</p>
  </div>
  <div class="content-card user-card">
    <RouterLink
      class="link-text"
      to="/user/appointments"
    >
      ← 返回我的预约
    </RouterLink>
    <StatePanel
      v-if="loading && !record"
      state="loading"
    />
    <StatePanel
      v-else-if="error && !record"
      state="error"
      @retry="load"
    />
    <template v-else-if="record">
      <div class="detail-title">
        <div>
          <h2>{{ record.itemName }}</h2><p class="muted">
            {{ record.outletName }}
          </p>
        </div><el-tag size="large">
          {{ appointmentStatusText[record.status] }}
        </el-tag>
      </div>
      <dl class="detail-grid">
        <div><dt>办理日期</dt><dd>{{ record.serviceDate }}</dd></div><div><dt>预约时段</dt><dd>{{ record.slotStartTime.slice(0, 5) }}–{{ record.slotEndTime.slice(0, 5) }}</dd></div><div><dt>预约编号</dt><dd>{{ record.appointmentId }}</dd></div>
      </dl>
      <div
        v-if="record.status === 'PENDING_CONFIRM'"
        class="notice-box"
      >
        <strong>请确认预约</strong><p>确认截止倒计时：{{ countdown }}。倒计时只作提醒，是否可确认由服务端裁决。</p><div class="action-row">
          <el-button
            type="primary"
            :loading="action === 'confirm'"
            :disabled="!!action"
            @click="command('confirm')"
          >
            确认预约
          </el-button><el-button
            :loading="action === 'cancel'"
            :disabled="!!action"
            @click="command('cancel')"
          >
            取消预约
          </el-button>
        </div>
      </div>
      <div
        v-else-if="record.status === 'CONFIRMED'"
        class="notice-box"
      >
        <strong>签到二维码</strong><p>到达网点后，在可签到时段内获取并出示二维码，也可点击现场签到。二维码约 2 分钟有效，刷新后旧码失效。</p><div
          v-if="qr"
          class="qr-wrap"
        >
          <img
            :src="qr"
            alt="签到二维码"
          ><span>有效期剩余 {{ qrCountdown }}</span>
        </div><div class="action-row">
          <el-button
            :loading="qrBusy"
            :disabled="!!action"
            @click="issueQr"
          >
            {{ qr ? '刷新二维码' : '获取二维码' }}
          </el-button><el-button
            v-if="qrToken"
            type="primary"
            :loading="action === 'check-in'"
            :disabled="!!action || qrBusy"
            @click="checkIn"
          >
            现场签到
          </el-button>
        </div>
      </div>
      <p
        v-if="checkInNotice"
        role="status"
        class="notice-box"
      >
        {{ checkInNotice }}
      </p>
      <div
        v-if="record.status === 'CONFIRMED'"
        class="action-row"
      >
        <el-button
          :loading="action === 'cancel'"
          :disabled="!!action"
          @click="command('cancel')"
        >
          取消预约
        </el-button>
      </div>
      <div
        v-if="record.status === 'CHECKED_IN' || record.status === 'SERVING'"
        class="notice-box"
      >
        <strong>排队进度</strong><template v-if="progress">
          <p>排队号：{{ progress.ticket.ticketNo }} · 当前状态：{{ queueStatusText[progress.ticket.status] ?? progress.ticket.status }}</p><p>前方 {{ progress.aheadCount }} 人<span v-if="progress.currentCall"> · 当前叫号 {{ progress.currentCall }}</span></p>
        </template><p v-else>
          正在读取排队票；签到建票可能需要片刻。
        </p><el-button @click="loadQueue">
          刷新进度
        </el-button>
      </div>
      <div
        v-if="error"
        class="inline-error"
        role="alert"
      >
        最新状态读取失败，请刷新后再操作。<el-button
          text
          @click="load"
        >
          重试
        </el-button>
      </div>
      <p
        v-if="!canCancel(record.status) && !['CHECKED_IN', 'SERVING'].includes(record.status)"
        class="muted"
      >
        此预约当前没有可执行操作。
      </p>
    </template>
  </div>
</template>
