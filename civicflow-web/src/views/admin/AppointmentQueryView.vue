<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { adminApi } from '@/api/admin'
import StatePanel from '@/components/StatePanel.vue'
import { adminErrorMessage } from '@/utils/adminError'
import { appointmentStatusText } from '@/utils/userFlow'
import type { AdminAppointment } from '@/types/admin'

const rows = ref<AdminAppointment[]>([]), page = ref(1), total = ref(0)
const userId = ref(''), outletId = ref(''), serviceDate = ref(''), status = ref('')
const loading = ref(false), error = ref(''), detail = ref<AdminAppointment | null>(null), detailOpen = ref(false)
let generation = 0, detailGeneration = 0, disposed = false
async function load() {
  if ([userId.value, outletId.value].some(value => value && !/^[1-9][0-9]{0,18}$/.test(value))) { error.value = '用户和网点编号须为正整数。'; return }
  const requestGeneration = ++generation
  loading.value = true; error.value = ''
  try {
    const result = await adminApi.appointments({ page: page.value, size: 20, userId: userId.value || undefined, outletId: outletId.value || undefined, serviceDate: serviceDate.value || undefined, status: status.value || undefined })
    if (!disposed && requestGeneration === generation) { rows.value = result.items; total.value = result.total }
  } catch (failure) { if (!disposed && requestGeneration === generation) error.value = adminErrorMessage(failure) }
  finally { if (!disposed && requestGeneration === generation) loading.value = false }
}
function search() { page.value = 1; void load() }
async function showDetail(row: AdminAppointment) {
  const requestGeneration = ++detailGeneration
  detailOpen.value = false
  try {
    const result = await adminApi.appointment(row.appointment.appointmentId)
    if (!disposed && requestGeneration === detailGeneration) { detail.value = result; detailOpen.value = true }
  } catch (failure) { if (!disposed && requestGeneration === detailGeneration) error.value = adminErrorMessage(failure) }
}
onMounted(() => { void load() })
onUnmounted(() => { disposed = true; generation += 1; detailGeneration += 1 })
</script>

<template>
  <div class="page-heading">
    <span class="eyebrow">管理员 / 预约管理</span><h1>预约查询</h1><p>按用户、网点、日期和状态查找预约，查看办理进度与操作记录。</p>
  </div>
  <div class="content-card admin-card">
    <form
      class="admin-toolbar"
      @submit.prevent="search"
    >
      <el-input
        v-model="userId"
        placeholder="用户编号"
        aria-label="用户编号"
        maxlength="19"
        clearable
      />
      <el-input
        v-model="outletId"
        placeholder="网点编号"
        aria-label="网点编号"
        maxlength="19"
        clearable
      />
      <input
        v-model="serviceDate"
        type="date"
        aria-label="办理日期"
      >
      <el-select
        v-model="status"
        placeholder="全部状态"
        clearable
      >
        <el-option
          v-for="(label, value) in appointmentStatusText"
          :key="value"
          :label="label"
          :value="value"
        />
      </el-select>
      <el-button
        native-type="submit"
        type="primary"
        :loading="loading"
      >
        查询预约
      </el-button>
    </form>
    <p
      v-if="error"
      class="admin-error"
      role="alert"
    >
      {{ error }}
    </p>
    <StatePanel
      v-if="loading"
      state="loading"
    />
    <StatePanel
      v-else-if="!error && !rows.length"
      state="empty"
      title="没有符合条件的预约"
      description="调整筛选条件后再查询。"
    />
    <el-table
      v-else-if="!error"
      :data="rows"
      stripe
    >
      <el-table-column
        prop="appointment.appointmentId"
        label="预约编号"
        min-width="180"
      />
      <el-table-column
        prop="userId"
        label="用户编号"
        min-width="160"
      />
      <el-table-column
        prop="appointment.outletName"
        label="网点"
        min-width="140"
      />
      <el-table-column
        prop="appointment.itemName"
        label="事项"
        min-width="140"
      />
      <el-table-column
        prop="appointment.serviceDate"
        label="办理日期"
        width="120"
      />
      <el-table-column
        label="状态"
        width="100"
      >
        <template #default="{ row }">
          {{ appointmentStatusText[row.appointment.status as keyof typeof appointmentStatusText] }}
        </template>
      </el-table-column>
      <el-table-column
        label="操作"
        min-width="150"
      >
        <template #default="{ row }">
          <el-button
            text
            @click="showDetail(row)"
          >
            详情
          </el-button><RouterLink
            class="link-text"
            :to="{ path: '/admin/operation-logs', query: { appointmentId: row.appointment.appointmentId } }"
          >
            操作记录
          </RouterLink>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      v-if="!error && total > 20"
      v-model:current-page="page"
      layout="prev, pager, next"
      :total="total"
      @current-change="load"
    />
  </div>
  <el-dialog
    v-model="detailOpen"
    title="预约详情"
    width="min(680px, 94vw)"
  >
    <dl
      v-if="detail"
      class="detail-grid"
    >
      <div><dt>预约编号</dt><dd>{{ detail.appointment.appointmentId }}</dd></div><div><dt>用户编号</dt><dd>{{ detail.userId }}</dd></div>
      <div><dt>服务网点</dt><dd>{{ detail.appointment.outletName }}</dd></div><div><dt>办理事项</dt><dd>{{ detail.appointment.itemName }}</dd></div>
      <div><dt>办理日期</dt><dd>{{ detail.appointment.serviceDate }}</dd></div><div><dt>预约时段</dt><dd>{{ detail.appointment.slotStartTime.slice(0, 5) }}–{{ detail.appointment.slotEndTime.slice(0, 5) }}</dd></div>
      <div><dt>当前状态</dt><dd>{{ appointmentStatusText[detail.appointment.status] }}</dd></div><div><dt>创建时间</dt><dd>{{ new Date(detail.appointment.createdAt).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai', hour12: false }) }}</dd></div>
    </dl>
  </el-dialog>
</template>
