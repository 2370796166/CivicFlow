<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import StatePanel from '@/components/StatePanel.vue'
import { userApi } from '@/api/user'
import type { Outlet, ServiceItem } from '@/types/user'

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
onMounted(() => { void (outletId.value ? loadOutlet() : loadOutlets()) })
</script>

<template>
  <div class="page-heading"><span class="eyebrow">普通用户 / 服务预约</span><h1>{{ outletId ? '选择办理事项' : '查找服务网点' }}</h1><p>按网点、事项和日期查找可办理服务。</p></div>
  <div v-if="!outletId" class="content-card user-card">
    <div class="search-row"><el-input v-model="keyword" placeholder="搜索网点名称" clearable @keyup.enter="search" /><el-button type="primary" @click="search">搜索</el-button></div>
    <StatePanel v-if="loading" state="loading" />
    <StatePanel v-else-if="error" state="error" @retry="loadOutlets" />
    <StatePanel v-else-if="!outlets.length" state="empty" title="没有找到可用网点" description="试试其他关键词，或稍后再查看。" />
    <div v-else class="user-list"><button v-for="item in outlets" :key="item.id" class="user-list-item" @click="router.push(`/user/outlets/${item.id}`)"><strong>{{ item.name }}</strong><span>{{ item.address }}</span><span class="link-text">查看事项 →</span></button></div>
    <el-pagination v-if="total > 20" v-model:current-page="page" background layout="prev, pager, next" :total="total" @current-change="loadOutlets" />
  </div>
  <div v-else class="content-card user-card">
    <RouterLink class="link-text" to="/user">← 返回网点列表</RouterLink>
    <StatePanel v-if="loading" state="loading" />
    <StatePanel v-else-if="error" state="error" @retry="loadOutlet" />
    <template v-else-if="outlet">
      <h2>{{ outlet.name }}</h2><p class="muted">{{ outlet.address }}<span v-if="outlet.maskedContactPhone"> · 电话 {{ outlet.maskedContactPhone }}</span></p>
      <h3>办理事项</h3>
      <StatePanel v-if="!items.length" state="empty" title="暂无可办理事项" />
      <div v-else class="choice-list"><label v-for="item in items" :key="item.id" class="choice-row"><input v-model="selectedItem" type="radio" name="item" :value="item.id" /><span><strong>{{ item.name }}</strong><small>{{ item.description || `预计办理 ${item.defaultDurationMinutes} 分钟` }}</small></span></label></div>
      <el-pagination v-if="itemTotal > 20" v-model:current-page="itemPage" background layout="prev, pager, next" :total="itemTotal" @current-change="loadOutlet" />
      <div v-if="selectedItem" class="date-selection"><label for="service-date">选择服务日期</label><input id="service-date" v-model="selectedDate" type="date" /></div>
      <div v-if="selectedItem && selectedDate" class="notice-box" role="status"><strong>号源查询暂未开放</strong><p>当前服务没有提供普通用户号源查询接口，暂时无法显示时段、放号状态或提交抢号。请稍后再试。</p></div>
    </template>
  </div>
</template>
