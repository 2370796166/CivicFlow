<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import StatePanel from '@/components/StatePanel.vue'
import { userApi } from '@/api/user'
import type { Outlet, ServiceItem, UserSlot } from '@/types/user'
import type { ApiError } from '@/types/api'
import { bookingErrorMessage } from '@/utils/bookingError'

const route = useRoute()
const router = useRouter()
const keyword = ref('')
const outlets = ref<Outlet[]>([])
const outlet = ref<Outlet | null>(null)
const items = ref<ServiceItem[]>([])
const selectedItem = ref('')
const selectedDate = ref('')
const page = ref(1)
const total = ref(0)
const itemPage = ref(1)
const itemTotal = ref(0)
const loading = ref(false)
const error = ref(false)
const outletId = ref(String(route.params.outletId ?? ''))
const slots = ref<UserSlot[]>([])
const slotPage = ref(1), slotTotal = ref(0), slotsLoading = ref(false), slotsError = ref(false)
const reserving = ref(''), reservationNotice = ref('')
const requestKeys = new Map<string, string>()
const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date())
const maxDate = new Date(Date.parse(`${today}T00:00:00Z`) + 365 * 86400000).toISOString().slice(0, 10)
const slotStatus = { BOOKABLE: '预约此时段', UPCOMING: '尚未放号', SUSPENDED: '暂停预约', CLOSED: '预约已截止' }
let slotGeneration = 0, contextGeneration = 0, disposed = false
let slotTimer: ReturnType<typeof setTimeout> | undefined

async function loadSlots() {
  clearTimeout(slotTimer)
  const generation = ++slotGeneration
  if (!outletId.value || !selectedItem.value || !selectedDate.value) return
  slotsLoading.value = true; slotsError.value = false
  try {
    const result = await userApi.slots(outletId.value, selectedItem.value, selectedDate.value, slotPage.value)
    if (disposed || generation !== slotGeneration) return
    slots.value = result.items; slotTotal.value = result.total
  } catch { if (!disposed && generation === slotGeneration) slotsError.value = true }
  finally {
    if (!disposed && generation === slotGeneration) {
      slotsLoading.value = false
      if (!document.hidden) slotTimer = setTimeout(() => { void loadSlots() }, 10000)
    }
  }
}
async function reserve(slot: UserSlot) {
  if (reserving.value || slotsLoading.value || slot.bookingStatus !== 'BOOKABLE') return
  reserving.value = slot.id; reservationNotice.value = ''
  const key = requestKeys.get(slot.id) ?? crypto.randomUUID()
  requestKeys.set(slot.id, key)
  try {
    const result = await userApi.reserve(slot.id, key)
    await router.push(`/user/reservations/${result.reservationId}`)
  } catch (error) {
    const failure = error as ApiError
    if (failure.status !== undefined && failure.status >= 400 && failure.status < 500 && ![408, 429].includes(failure.status)) requestKeys.delete(slot.id)
    reservationNotice.value = bookingErrorMessage(failure.code,
      failure.status === undefined || failure.status >= 500 ? '请求结果暂不确定。再次点击此时段会核实同一请求，也可到“我的预约”查看。'
      : '号源状态已变化，请刷新后重新选择。')
    void loadSlots()
  } finally { reserving.value = '' }
}
function visibilityChanged() {
  clearTimeout(slotTimer)
  if (!document.hidden && selectedItem.value && selectedDate.value) void loadSlots()
}
watch([selectedItem, selectedDate], () => {
  slotGeneration += 1; clearTimeout(slotTimer); slots.value = []; slotTotal.value = 0
  slotsLoading.value = false; slotsError.value = false; slotPage.value = 1; reservationNotice.value = ''
  if (selectedItem.value && selectedDate.value) void loadSlots()
})

async function loadOutlets() {
  const generation = ++contextGeneration
  loading.value = true; error.value = false
  try { const result = await userApi.outlets(keyword.value.trim(), page.value); if (!disposed && generation === contextGeneration) { outlets.value = result.items; total.value = result.total } }
  catch { if (!disposed && generation === contextGeneration) error.value = true }
  finally { if (!disposed && generation === contextGeneration) loading.value = false }
}
async function loadOutlet() {
  if (!outletId.value) return loadOutlets()
  const generation = ++contextGeneration
  loading.value = true; error.value = false
  try {
    const id = outletId.value
    const [nextOutlet, result] = await Promise.all([userApi.outlet(id), userApi.items(id, itemPage.value)])
    if (!disposed && generation === contextGeneration) { outlet.value = nextOutlet; items.value = result.items; itemTotal.value = result.total }
  } catch { if (!disposed && generation === contextGeneration) error.value = true }
  finally { if (!disposed && generation === contextGeneration) loading.value = false }
}
function search() { page.value = 1; void loadOutlets() }
watch(() => route.params.outletId, (id) => {
  outletId.value = String(id ?? ''); selectedItem.value = ''; selectedDate.value = ''; outlet.value = null; itemPage.value = 1
  void (outletId.value ? loadOutlet() : loadOutlets())
})
onMounted(() => { document.addEventListener('visibilitychange', visibilityChanged); void (outletId.value ? loadOutlet() : loadOutlets()) })
onUnmounted(() => { disposed = true; slotGeneration += 1; contextGeneration += 1; clearTimeout(slotTimer); document.removeEventListener('visibilitychange', visibilityChanged) })
</script>

<template>
  <div class="page-heading">
    <span class="eyebrow">普通用户 / 服务预约</span><h1>{{ outletId ? '选择办理事项' : '查找服务网点' }}</h1><p>按网点、事项和日期查找可办理服务。</p>
  </div>
  <div
    v-if="!outletId"
    class="content-card user-card"
  >
    <div class="search-row">
      <el-input
        v-model="keyword"
        placeholder="搜索网点名称"
        clearable
        @keyup.enter="search"
      /><el-button
        type="primary"
        @click="search"
      >
        搜索
      </el-button>
    </div>
    <StatePanel
      v-if="loading"
      state="loading"
    />
    <StatePanel
      v-else-if="error"
      state="error"
      @retry="loadOutlets"
    />
    <StatePanel
      v-else-if="!outlets.length"
      state="empty"
      title="没有找到可用网点"
      description="试试其他关键词，或稍后再查看。"
    />
    <div
      v-else
      class="user-list"
    >
      <button
        v-for="item in outlets"
        :key="item.id"
        class="user-list-item"
        @click="router.push(`/user/outlets/${item.id}`)"
      >
        <strong>{{ item.name }}</strong><span>{{ item.address }}</span><span class="link-text">查看事项 →</span>
      </button>
    </div>
    <el-pagination
      v-if="total > 20"
      v-model:current-page="page"
      background
      layout="prev, pager, next"
      :total="total"
      @current-change="loadOutlets"
    />
  </div>
  <div
    v-else
    class="content-card user-card"
  >
    <RouterLink
      class="link-text"
      to="/user"
    >
      ← 返回网点列表
    </RouterLink>
    <StatePanel
      v-if="loading"
      state="loading"
    />
    <StatePanel
      v-else-if="error"
      state="error"
      @retry="loadOutlet"
    />
    <template v-else-if="outlet">
      <h2>{{ outlet.name }}</h2><p class="muted">
        {{ outlet.address }}<span v-if="outlet.maskedContactPhone"> · 电话 {{ outlet.maskedContactPhone }}</span>
      </p>
      <h3>办理事项</h3>
      <StatePanel
        v-if="!items.length"
        state="empty"
        title="暂无可办理事项"
      />
      <div
        v-else
        class="choice-list"
      >
        <label
          v-for="item in items"
          :key="item.id"
          class="choice-row"
        ><input
          v-model="selectedItem"
          type="radio"
          name="item"
          :value="item.id"
          :disabled="!!reserving"
        ><span><strong>{{ item.name }}</strong><small>{{ item.description || `预计办理 ${item.defaultDurationMinutes} 分钟` }}</small></span></label>
      </div>
      <el-pagination
        v-if="itemTotal > 20"
        v-model:current-page="itemPage"
        background
        layout="prev, pager, next"
        :total="itemTotal"
        @current-change="loadOutlet"
      />
      <div
        v-if="selectedItem"
        class="date-selection"
      >
        <label for="service-date">选择服务日期</label><input
          id="service-date"
          v-model="selectedDate"
          type="date"
          :min="today"
          :max="maxDate"
          :disabled="!!reserving"
        >
      </div>
      <section
        v-if="selectedItem && selectedDate"
        aria-label="预约时段"
      >
        <div class="slot-heading">
          <h3>选择预约时段</h3><el-button
            :loading="slotsLoading"
            :disabled="!!reserving"
            @click="loadSlots"
          >
            刷新号源
          </el-button>
        </div>
        <p class="muted">
          名额以提交预约时的结果为准。预约受理后，请在五分钟内确认。
        </p>
        <StatePanel
          v-if="slotsLoading && !slots.length"
          state="loading"
          title="正在查询号源"
        />
        <StatePanel
          v-else-if="slotsError"
          state="error"
          title="号源读取失败"
          description="请刷新号源后重试。"
          @retry="loadSlots"
        />
        <StatePanel
          v-else-if="!slots.length"
          state="empty"
          title="当天暂无预约时段"
          description="请选择其他日期，或稍后再次查询。"
        />
        <div
          v-else
          class="booking-slots"
        >
          <article
            v-for="slot in slots"
            :key="slot.id"
            class="booking-slot"
            :class="{ 'booking-slot-open': slot.bookingStatus === 'BOOKABLE' }"
          >
            <strong>{{ slot.startTime.slice(0, 5) }}–{{ slot.endTime.slice(0, 5) }}</strong>
            <span>时段总名额 {{ slot.totalQuota }}</span>
            <small v-if="slot.bookingStatus === 'UPCOMING'">{{ new Date(slot.releaseAt).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai', hour12: false }) }} 放号</small>
            <el-button
              :type="slot.bookingStatus === 'BOOKABLE' ? 'primary' : 'default'"
              :loading="reserving === slot.id"
              :disabled="!!reserving || slotsLoading || slot.bookingStatus !== 'BOOKABLE'"
              @click="reserve(slot)"
            >
              {{ slotStatus[slot.bookingStatus] }}
            </el-button>
          </article>
        </div>
        <el-pagination
          v-if="slotTotal > 20"
          v-model:current-page="slotPage"
          layout="prev, pager, next"
          :total="slotTotal"
          :disabled="!!reserving"
          @current-change="loadSlots"
        />
        <div
          v-if="reservationNotice"
          class="notice-box"
          role="alert"
        >
          <p>{{ reservationNotice }}</p><RouterLink
            to="/user/appointments"
            class="link-text"
          >
            查看我的预约
          </RouterLink>
        </div>
      </section>
    </template>
  </div>
</template>
