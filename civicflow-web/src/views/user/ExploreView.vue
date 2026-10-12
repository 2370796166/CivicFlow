<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import StatePanel from '@/components/StatePanel.vue'
import { userApi } from '@/api/user'
import type { Outlet, ServiceItem, UserSlot } from '@/types/user'

const route = useRoute()
const router = useRouter()
const keyword = ref('')
const outlets = ref<Outlet[]>([])
const outlet = ref<Outlet | null>(null)
const items = ref<ServiceItem[]>([])
const selectedItem = ref('')
const selectedDate = ref('')
const slots = ref<UserSlot[]>([])
const slotsLoading = ref(false)
const slotsError = ref(false)
const reserving = ref('')
const now = ref(Date.now())
const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date())
const slotLabels: Record<UserSlot['availability'], string> = { NOT_RELEASED: '尚未放号', AVAILABLE: '可预约', FULL: '已约满', CLOSED: '已停售', UNKNOWN: '余量待核实' }
const selection = computed(() => outletId.value + ':' + selectedItem.value + ':' + selectedDate.value)
let slotGeneration = 0
let refreshTimer: ReturnType<typeof setInterval> | undefined
async function loadSlots() {
  const generation = ++slotGeneration
  if (!outletId.value || !selectedItem.value || !selectedDate.value) { slots.value = []; return }
  slotsLoading.value = true; slotsError.value = false
  try { const result = await userApi.slots(outletId.value, selectedItem.value, selectedDate.value); if (generation === slotGeneration) slots.value = result }
  catch { if (generation === slotGeneration) slotsError.value = true }
  finally { if (generation === slotGeneration) slotsLoading.value = false }
}
function canReserve(slot: UserSlot) { return ['AVAILABLE', 'UNKNOWN'].includes(slot.availability) && Date.parse(slot.configuration.releaseAt) <= now.value && Date.parse(slot.configuration.closeAt) > now.value }
async function reserve(slot: UserSlot) {
  if (reserving.value || !canReserve(slot)) return
  const id = slot.configuration.slotId
  const storageKey = 'civicflow-reservation-key:' + id
  const key = sessionStorage.getItem(storageKey) ?? crypto.randomUUID()
  sessionStorage.setItem(storageKey, key)
  reserving.value = id
  try {
    const result = await userApi.reserve(id, key)
    sessionStorage.removeItem(storageKey)
    await router.push('/user/reservations/' + result.reservationId)
  } catch (error) {
    const status = (error as { status?: number }).status
    // Only a definite business rejection permits a new intent. Unknown outcomes retain the key.
    if (status === 400 || status === 409 || status === 422) sessionStorage.removeItem(storageKey)
    await loadSlots()
  } finally { reserving.value = '' }
}
const page = ref(1)
const total = ref(0)
const itemPage = ref(1)
const itemTotal = ref(0)
const loading = ref(false)
const error = ref(false)
const outletId = ref(String(route.params.outletId ?? ''))
watch(selection, () => { slots.value = []; void loadSlots() })

async function loadOutlets() {
  loading.value = true; error.value = false
  try { const result = await userApi.outlets(keyword.value.trim(), page.value); outlets.value = result.items; total.value = result.total }
  catch { error.value = true }
  finally { loading.value = false }
}
async function loadOutlet() {
  if (!outletId.value) return loadOutlets()
  loading.value = true; error.value = false
  try {
    outlet.value = await userApi.outlet(outletId.value)
    const result = await userApi.items(outletId.value, itemPage.value)
    items.value = result.items; itemTotal.value = result.total
  } catch { error.value = true }
  finally { loading.value = false }
}
function search() { page.value = 1; void loadOutlets() }
watch(() => route.params.outletId, (id) => {
  outletId.value = String(id ?? ''); selectedItem.value = ''; selectedDate.value = ''; outlet.value = null; itemPage.value = 1
  void (outletId.value ? loadOutlet() : loadOutlets())
})
onMounted(() => {
  void (outletId.value ? loadOutlet() : loadOutlets())
  refreshTimer = setInterval(() => { now.value = Date.now(); if (!document.hidden && selectedItem.value && selectedDate.value && !slotsLoading.value && !reserving.value) void loadSlots() }, 10000)
})
onUnmounted(() => { ++slotGeneration; clearInterval(refreshTimer) })
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
        >
      </div>
      <section
        v-if="selectedItem && selectedDate"
        aria-label="可预约时段"
      >
        <div class="action-row">
          <h3>预约时段</h3><el-button
            :loading="slotsLoading"
            @click="loadSlots"
          >
            刷新号源
          </el-button>
        </div>
        <StatePanel
          v-if="slotsLoading && !slots.length"
          state="loading"
        />
        <StatePanel
          v-else-if="slotsError"
          state="error"
          title="号源查询失败"
          @retry="loadSlots"
        />
        <StatePanel
          v-else-if="!slots.length"
          state="empty"
          title="当天暂无号源"
          description="请选择其他日期。"
        />
        <div
          v-else
          class="choice-list"
        >
          <div
            v-for="slot in slots"
            :key="slot.configuration.slotId"
            class="choice-row"
          >
            <div>
              <strong>{{ slot.configuration.startTime.slice(0, 5) }}–{{ slot.configuration.endTime.slice(0, 5) }}</strong>
              <p>{{ slotLabels[slot.availability] }}<span v-if="slot.remaining !== null"> · 剩余 {{ slot.remaining }}</span></p>
              <small v-if="slot.availability === 'NOT_RELEASED'">放号时间：{{ new Date(slot.configuration.releaseAt).toLocaleString() }}</small>
            </div>
            <el-button
              type="primary"
              :disabled="!canReserve(slot) || !!reserving"
              :loading="reserving === slot.configuration.slotId"
              @click="reserve(slot)"
            >
              预约
            </el-button>
          </div>
        </div><p class="muted">
          余量会动态变化，预约结果以提交后返回的状态为准。网络中断后重试会继续核实同一次预约。
        </p>
      </section>
    </template>
  </div>
</template>
