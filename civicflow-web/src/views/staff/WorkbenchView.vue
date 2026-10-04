<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { staffApi } from '@/api/staff'
import StatePanel from '@/components/StatePanel.vue'
import type { ApiError } from '@/types/api'
import type { CurrentWorkSession, StaffAction, StaffScope } from '@/types/staff'
import { actionLabel, actionSignature, availableActions, confirmText } from '@/utils/staffFlow'

const scopes = ref<StaffScope[]>([])
const outletId = ref('')
const windowId = ref('')
const current = ref<CurrentWorkSession | null>(null)
const scopesLoading = ref(false)
const scopesError = ref(false)
const refreshing = ref(false)
const currentError = ref(false)
const uncertain = ref(false)
const busy = ref<StaffAction | null>(null)
const notice = ref('')
const keys = new Map<StaffAction, { signature: string; key: string }>()
let timer: ReturnType<typeof setTimeout> | undefined
let active = true
let generation = 0

const outlets = computed(() => [...new Map(scopes.value.map((scope) => [scope.outletId, { id: scope.outletId, name: scope.outletName }])).values()])
const windows = computed(() => scopes.value.filter((scope) => scope.outletId === outletId.value))
const selected = computed(() => windows.value.find((scope) => scope.windowId === windowId.value) ?? null)
const actions = computed(() => availableActions(current.value))
const ticket = computed(() => current.value?.currentTicket ?? null)
const controlsLocked = computed(() => !!busy.value || uncertain.value || currentError.value || refreshing.value)
const configured = Number(import.meta.env.VITE_STAFF_POLL_MS)
const pollMs = Number.isFinite(configured) && configured >= 3000 && configured <= 10000 ? configured : 4000

function schedule(delay = document.hidden ? 15000 : pollMs) {
  clearTimeout(timer)
  if (active && windowId.value) timer = setTimeout(() => { void refreshCurrent() }, delay)
}
async function loadScopes() {
  scopesLoading.value = true; scopesError.value = false
  try {
    scopes.value = await staffApi.scopes()
    if (!active) return
    if (!scopes.value.some((scope) => scope.outletId === outletId.value)) outletId.value = scopes.value[0]?.outletId ?? ''
    if (!scopes.value.some((scope) => scope.windowId === windowId.value && scope.outletId === outletId.value)) windowId.value = windows.value[0]?.windowId ?? ''
    if (windowId.value) await refreshCurrent(true)
  } catch { scopesError.value = true }
  finally { scopesLoading.value = false }
}
async function refreshCurrent(force = false): Promise<boolean> {
  if (!windowId.value || (!force && (refreshing.value || busy.value))) { schedule(); return false }
  const selectedWindow = windowId.value
  const requestGeneration = generation
  refreshing.value = true
  try {
    const next = await staffApi.current(selectedWindow)
    if (!active || requestGeneration !== generation || selectedWindow !== windowId.value) return false
    current.value = next
    for (const [action, saved] of keys) {
      if (!availableActions(next).includes(action) || actionSignature(action, next, selectedWindow) !== saved.signature) keys.delete(action)
    }
    currentError.value = false
    uncertain.value = false
    return true
  } catch {
    if (active && requestGeneration === generation) currentError.value = true
    return false
  } finally {
    refreshing.value = false
    schedule()
  }
}
function selectOutlet() {
  windowId.value = windows.value[0]?.windowId ?? ''
  selectWindow()
}
function selectWindow() {
  generation += 1
  current.value = null; currentError.value = false; uncertain.value = false; notice.value = ''
  keys.clear()
  clearTimeout(timer)
  if (windowId.value) void refreshCurrent(true)
}

async function perform(action: StaffAction) {
  if (!selected.value || controlsLocked.value || !actions.value.includes(action)) return
  const before = current.value
  const signature = actionSignature(action, before, windowId.value)
  if (!signature) return
  busy.value = action
  notice.value = ''
  try {
    await ElMessageBox.confirm(confirmText[action], actionLabel[action], {
      confirmButtonText: `确认${actionLabel[action]}`, cancelButtonText: '返回工作台', type: ['end', 'miss', 'complete'].includes(action) ? 'warning' : 'info',
      closeOnClickModal: false, closeOnPressEscape: true,
    })
  } catch { busy.value = null; return }
  const saved = keys.get(action)
  const key = saved?.signature === signature ? saved.key : crypto.randomUUID()
  keys.set(action, { signature, key })
  try {
    if (action === 'start') await staffApi.start(windowId.value, key)
    else if (action === 'end' && before) await staffApi.end(before.session, key)
    else if (action === 'call-next' && before) {
      const next = await staffApi.callNext(before.session.id, key)
      if (!next) notice.value = '当前没有可叫的等待票。'
    } else if (before?.currentTicket) {
      const path = action === 'start-serving' ? 'start' : action
      if (path === 'recall' || path === 'miss' || path === 'start' || path === 'complete') await staffApi.change(path, before.currentTicket, before.session.id, key)
    }
    keys.delete(action)
    const fresh = await refreshCurrent(true)
    if (!fresh) { uncertain.value = true; notice.value = '操作已提交，但最新状态读取失败。请刷新状态后继续。' }
    else if (!notice.value) notice.value = `${actionLabel[action]}已提交，显示的是服务端最新状态。`
  } catch (error) {
    const apiError = error as ApiError
    uncertain.value = true
    const fresh = await refreshCurrent(true)
    if (apiError.status === 409) {
      if (apiError.code === 'COMMON_409_IDEMPOTENCY_CONFLICT') keys.delete(action)
      notice.value = apiError.code === 'COMMON_409_IDEMPOTENCY_CONFLICT'
        ? '操作凭证与请求不一致，已刷新状态。请核对后重新操作。'
        : apiError.code === 'QUEUE_409_SESSION_ACTIVE'
        ? '该窗口已有工作会话，已刷新最新状态。'
        : '票据或窗口状态已变化，已刷新最新状态。请核对后再操作。'
    } else {
      notice.value = fresh ? '请求结果可能已生效，已读取服务端最新状态。请核对后再操作。' : '请求结果暂不确定。请刷新状态后再操作；重试会使用同一幂等键。'
    }
    if (!fresh) uncertain.value = true
    ElMessage.warning(notice.value)
  } finally { busy.value = null }
}
function visibilityChanged() { schedule(document.hidden ? 15000 : 0) }
onMounted(() => { active = true; document.addEventListener('visibilitychange', visibilityChanged); void loadScopes() })
onUnmounted(() => { active = false; generation += 1; clearTimeout(timer); document.removeEventListener('visibilitychange', visibilityChanged) })
</script>

<template>
  <div class="staff-workbench">
    <div class="page-heading"><span class="eyebrow">窗口人员 / 今日工作</span><h1>窗口工作台</h1><p>先选择授权窗口，再按现场顺序叫号与办理。</p></div>
    <StatePanel v-if="scopesLoading" state="loading" />
    <StatePanel v-else-if="scopesError" state="error" title="授权窗口读取失败" @retry="loadScopes" />
    <StatePanel v-else-if="!scopes.length" state="empty" title="暂无授权窗口" description="请联系管理员配置网点和窗口授权。" />
    <template v-else>
      <section class="staff-selector" aria-label="选择工作窗口">
        <div><label for="staff-outlet">授权网点</label><select id="staff-outlet" v-model="outletId" :disabled="!!current || !!busy" @change="selectOutlet"><option v-for="outlet in outlets" :key="outlet.id" :value="outlet.id">{{ outlet.name }}</option></select></div>
        <div><label for="staff-window">工作窗口</label><select id="staff-window" v-model="windowId" :disabled="!!current || !!busy" @change="selectWindow"><option v-for="scope in windows" :key="scope.windowId" :value="scope.windowId">{{ scope.windowName }}</option></select></div>
        <div class="staff-selector-note"><span>可办事项</span><strong>{{ selected?.items.map((item) => item.name).join('、') || '无' }}</strong></div>
      </section>

      <div v-if="notice" class="staff-notice" role="status">{{ notice }}</div>
      <div v-if="currentError" class="staff-error" role="alert">最新状态读取失败。操作已暂时锁定。<button type="button" @click="refreshCurrent(true)">刷新状态</button></div>
      <section class="staff-stage" aria-label="当前工作状态">
        <div class="staff-stage-head"><span>{{ selected?.windowName }}</span><strong :class="current ? 'session-active' : 'session-idle'">{{ current ? '工作中' : '未开工' }}</strong></div>
        <div v-if="ticket" class="staff-ticket"><span class="staff-ticket-label">{{ ticket.status === 'SERVING' ? '正在办理' : '当前已叫号' }}</span><strong>{{ ticket.ticketNo }}</strong><span>已呼叫 {{ ticket.callCount }} 次</span></div>
        <div v-else class="staff-ticket staff-ticket-empty"><span class="staff-ticket-label">当前办理票</span><strong>—</strong><span>{{ current ? '窗口空闲，可叫下一号' : '开始工作后可叫号' }}</span></div>
      </section>

      <section class="staff-controls" aria-label="窗口操作">
        <button v-for="action in actions" :key="action" type="button" class="staff-action" :class="`staff-action-${action}`" :disabled="controlsLocked" @click="perform(action)">{{ busy === action ? '提交中…' : actionLabel[action] }}</button>
        <button type="button" class="staff-refresh" :disabled="!!busy || refreshing" @click="refreshCurrent(true)">刷新状态</button>
      </section>

      <section class="staff-snapshot" aria-label="队列概览">
        <div><span>等待人数</span><strong>—</strong><small>窗口接口暂未提供</small></div>
        <div><span>已叫号列表</span><strong>—</strong><small>仅可查看当前票</small></div>
        <div><span>过号列表</span><strong>—</strong><small>窗口接口暂未提供</small></div>
      </section>
      <p class="staff-contract-note">当前服务仅支持结束工作，没有暂停/恢复接口。结束前须先处理当前已叫号或办理中的票。状态约每 {{ pollMs / 1000 }} 秒刷新一次；页面隐藏时降低频率。</p>
    </template>
  </div>
</template>
