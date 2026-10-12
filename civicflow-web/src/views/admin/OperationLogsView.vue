<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { adminApi } from '@/api/admin'
import StatePanel from '@/components/StatePanel.vue'
import { adminErrorMessage } from '@/utils/adminError'
import { appointmentStatusText } from '@/utils/userFlow'
import type { AppointmentLog } from '@/types/admin'
import type { AppointmentStatus } from '@/types/user'

const route = useRoute()
const appointmentId = ref(String(route.query.appointmentId ?? '')), operation = ref('')
const rows = ref<AppointmentLog[]>([]), page = ref(1), total = ref(0), loading = ref(false), error = ref('')
const operations: Record<string, string> = { CREATE: '创建预约', CONFIRM: '确认预约', CANCEL: '取消预约', EXPIRE: '超时释放', CHECK_IN: '签到', LINK_QUEUE_TICKET: '关联排队票', START_SERVING: '开始办理', COMPLETE: '完成办理', MARK_NO_SHOW: '标记过号' }
const actors: Record<string, string> = { USER: '用户', ADMIN: '管理员', STAFF: '窗口人员', SERVICE: '系统服务', SYSTEM: '系统' }
let generation = 0, disposed = false
function statusText(value: string | null) { return value ? appointmentStatusText[value as AppointmentStatus] || value : '—' }
async function load() {
  if (appointmentId.value && !/^[1-9][0-9]{0,18}$/.test(appointmentId.value)) { error.value = '预约编号须为正整数。'; return }
  const requestGeneration = ++generation
  loading.value = true; error.value = ''
  try {
    const result = await adminApi.appointmentLogs({ page: page.value, size: 20, appointmentId: appointmentId.value || undefined, operation: operation.value || undefined })
    if (!disposed && requestGeneration === generation) { rows.value = result.items; total.value = result.total }
  } catch (failure) { if (!disposed && requestGeneration === generation) error.value = adminErrorMessage(failure) }
  finally { if (!disposed && requestGeneration === generation) loading.value = false }
}
function search() { page.value = 1; void load() }
watch(() => route.query.appointmentId, value => { appointmentId.value = String(value ?? ''); search() })
onMounted(() => { void load() })
onUnmounted(() => { disposed = true; generation += 1 })
</script>

<template>
  <div class="page-heading">
    <span class="eyebrow">管理员 / 预约审计</span><h1>预约操作日志</h1><p>查看预约从创建到办理结束的状态变更记录。</p>
  </div>
  <div class="content-card admin-card">
    <form
      class="admin-toolbar"
      @submit.prevent="search"
    >
      <el-input
        v-model="appointmentId"
        placeholder="预约编号"
        aria-label="预约编号"
        maxlength="19"
        clearable
      /><el-select
        v-model="operation"
        placeholder="全部操作"
        clearable
      >
        <el-option
          v-for="(label, value) in operations"
          :key="value"
          :label="label"
          :value="value"
        />
      </el-select><el-button
        native-type="submit"
        type="primary"
        :loading="loading"
      >
        查询日志
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
      title="暂无符合条件的操作记录"
      description="可输入预约编号查看其处理过程。"
    />
    <el-table
      v-else-if="!error"
      :data="rows"
      stripe
    >
      <el-table-column
        prop="appointmentId"
        label="预约编号"
        min-width="180"
      />
      <el-table-column
        label="操作"
        min-width="130"
      >
        <template #default="{ row }">
          {{ operations[row.operation] || row.operation }}
        </template>
      </el-table-column>
      <el-table-column
        label="状态变化"
        min-width="190"
      >
        <template #default="{ row }">
          {{ statusText(row.fromStatus) }} → {{ statusText(row.toStatus) }}
        </template>
      </el-table-column>
      <el-table-column
        label="操作者"
        min-width="170"
      >
        <template #default="{ row }">
          {{ actors[row.actorType] || row.actorType }} {{ row.actorId || '' }}
        </template>
      </el-table-column>
      <el-table-column
        label="发生时间"
        min-width="180"
      >
        <template #default="{ row }">
          {{ new Date(row.occurredAt).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai', hour12: false }) }}
        </template>
      </el-table-column>
      <el-table-column
        prop="requestId"
        label="请求编号"
        min-width="200"
      />
    </el-table>
    <el-pagination
      v-if="!error && total > 20"
      v-model:current-page="page"
      layout="prev, pager, next"
      :total="total"
      @current-change="load"
    />
  </div>
</template>
