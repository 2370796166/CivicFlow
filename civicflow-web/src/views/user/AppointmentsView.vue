<script setup lang="ts">
import { onMounted, ref } from 'vue'
import StatePanel from '@/components/StatePanel.vue'
import { userApi } from '@/api/user'
import { appointmentStatusText } from '@/utils/userFlow'
import type { Appointment, AppointmentStatus } from '@/types/user'

const records = ref<Appointment[]>([])
const page = ref(1)
const total = ref(0)
const status = ref('')
const loading = ref(false)
const error = ref(false)
async function load() {
  loading.value = true; error.value = false
  try { const result = await userApi.appointments(page.value, status.value || undefined); records.value = result.items; total.value = result.total }
  catch { error.value = true }
  finally { loading.value = false }
}
function filter() { page.value = 1; void load() }
onMounted(() => { void load() })
</script>
<template>
  <div class="page-heading"><span class="eyebrow">普通用户 / 我的服务</span><h1>我的预约</h1><p>查看确认、签到与办理进度。</p></div>
  <div class="content-card user-card">
    <div class="filter-row"><el-select v-model="status" placeholder="全部状态" clearable @change="filter"><el-option v-for="(label, value) in appointmentStatusText" :key="value" :label="label" :value="value" /></el-select><el-button @click="load">刷新</el-button></div>
    <StatePanel v-if="loading" state="loading" />
    <StatePanel v-else-if="error" state="error" @retry="load" />
    <StatePanel v-else-if="!records.length" state="empty" title="暂无预约" description="选择网点和事项后，预约会显示在这里。" />
    <div v-else class="user-list"><RouterLink v-for="item in records" :key="item.appointmentId" class="user-list-item" :to="`/user/appointments/${item.appointmentId}`"><span class="list-heading"><strong>{{ item.itemName }}</strong><el-tag>{{ appointmentStatusText[item.status as AppointmentStatus] ?? item.status }}</el-tag></span><span>{{ item.outletName }} · {{ item.serviceDate }} {{ item.slotStartTime.slice(0, 5) }}</span><span class="link-text">查看详情 →</span></RouterLink></div>
    <el-pagination v-if="total > 20" v-model:current-page="page" background layout="prev, pager, next" :total="total" @current-change="load" />
  </div>
</template>
