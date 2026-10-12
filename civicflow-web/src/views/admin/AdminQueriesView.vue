<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { ElMessageBox } from 'element-plus'
import { api } from '@/api/client'
import type { ApiResponse, Page } from '@/types/api'
import StatePanel from '@/components/StatePanel.vue'
import { appointmentStatusText } from '@/utils/userFlow'
const props = defineProps<{ kind: 'appointments' | 'operation-logs' | 'reconciliations' | 'statistics' | 'syncs' }>()
type Row = Record<string, string | number | null>
const rows = ref<Row[]>([]), total = ref(0), page = ref(1), loading = ref(false), error = ref(false)
const source = ref('appointment')
const performance = ref<Row[]>([])
const outletId = ref(''), itemId = ref(''), appointmentId = ref(''), requestId = ref(''), status = ref('')
const dateFrom = ref(new Date().toISOString().slice(0, 10)), dateTo = ref(new Date().toISOString().slice(0, 10))
const detail = ref<Row[]>([]), detailOpen = ref(false), detailLoading = ref(false), detailError = ref(false)
const selectedId = ref(''), detailPage = ref(1), detailTotal = ref(0)
let generation = 0
let detailGeneration = 0
const titles = { syncs: '排队同步异常', appointments: '预约查询', 'operation-logs': '预约操作日志', reconciliations: '对账历史', statistics: '预约统计', }
const columns = computed(() => props.kind === 'appointments' ? { appointmentId: '预约编号', outletName: '网点', itemName: '事项', serviceDate: '办理日期', slotStartTime: '开始时间', status: '状态' } : props.kind === 'operation-logs' ? (source.value === 'appointment' ? { appointmentId: '预约编号', actorType: '操作方', operation: '操作', fromStatus: '原状态', toStatus: '目标状态', requestId: '请求编号', occurredAt: '时间' } : { targetId: '业务编号', actorId: '操作人', operation: '操作', category: '类别', requestId: '请求编号', occurredAt: '时间' }) : props.kind === 'reconciliations' ? { id: '报告编号', triggerType: '触发方式', status: '状态', startedAt: '开始时间', completedAt: '结束时间' } : props.kind === 'syncs' ? { id: '任务编号', appointmentId: '预约编号', targetStatus: '目标状态', status: '任务状态', attempts: '尝试次数', lastErrorCode: '最近错误' } : { status: '预约状态', count: '数量' })
async function load() {
  const current = ++generation
  loading.value = true; error.value = false
  try {
    const params = props.kind === 'appointments' ? { page: page.value, outletId: outletId.value || undefined, itemId: itemId.value || undefined, status: status.value || undefined, dateFrom: dateFrom.value || undefined, dateTo: dateTo.value || undefined } : props.kind === 'operation-logs' ? { page: page.value, appointmentId: appointmentId.value || undefined, requestId: requestId.value || undefined } : props.kind === 'statistics' ? { dateFrom: dateFrom.value, dateTo: dateTo.value } : { page: page.value }
    const path = props.kind === 'statistics' ? '/admin/appointments/statistics' : props.kind === 'syncs' ? '/admin/queues/syncs' : props.kind === 'operation-logs' && source.value !== 'appointment' ? '/admin/audit/' + source.value : '/admin/' + props.kind
    const response = await api.get<ApiResponse<Page<Row> | Row[]>>(path, { params })
    if (current !== generation) return
    const data = response.data.data
    if (Array.isArray(data)) { rows.value = data; total.value = data.length }
    else { rows.value = data.items; total.value = data.total }
    if (props.kind === 'statistics') {
      const result = await api.get<ApiResponse<Row[]>>('/admin/queues/statistics', { params: { dateFrom: dateFrom.value, dateTo: dateTo.value } })
      if (current === generation) performance.value = result.data.data
    }
  } catch { if (current === generation) error.value = true }
  finally { if (current === generation) loading.value = false }
}
function search() { page.value = 1; void load() }
async function loadDetail() {
  const generation = ++detailGeneration
  detailLoading.value = true; detailError.value = false
  try {
    const path = props.kind === 'appointments' ? '/admin/operation-logs' : '/admin/reconciliations/' + selectedId.value
    const response = await api.get<ApiResponse<Page<Row>>>(path, { params: { page: detailPage.value, appointmentId: props.kind === 'appointments' ? selectedId.value : undefined } })
    if (generation !== detailGeneration) return
    detail.value = response.data.data.items; detailTotal.value = response.data.data.total
  } catch { if (generation === detailGeneration) detailError.value = true }
  finally { if (generation === detailGeneration) detailLoading.value = false }
}
function open(row: Row) { selectedId.value = String(row.appointmentId ?? row.id); detail.value = []; detailPage.value = 1; detailOpen.value = true; void loadDetail() }
const detailColumns = computed(() => props.kind === 'appointments' ? { operation: '操作', fromStatus: '原状态', toStatus: '目标状态', occurredAt: '时间' } : { slotId: '号源', configuredTotal: '总量', expectedRemaining: '应剩余', actualRemaining: '实际剩余', diff: '差异', classification: '分类', repairStatus: '修复状态' })
function value(row: Row, key: string) { const val = row[key]; if ((key === 'status' || key === 'fromStatus' || key === 'toStatus') && val) return appointmentStatusText[val as keyof typeof appointmentStatusText] ?? val; return val ?? '—' }
watch(() => props.kind, () => { rows.value = []; detailOpen.value = false; search() })
async function retrySync(row: Row) {
  let reason: string
  try {
    const result = await ElMessageBox.prompt('请填写恢复原因。该操作会记录审计日志，并使用原同步任务继续执行。', '重试同步', { inputValidator: (value) => Boolean(value?.trim()) && value.trim().length <= 256 || '请填写 1–256 字原因' })
    reason = result.value.trim()
  } catch { return }
  await api.post('/admin/queues/syncs/' + row.id + '/retry', { reason })
  await load()
}
function share(status: string) {
  const total = rows.value.reduce((sum, row) => sum + Number(row.count ?? 0), 0)
  const count = Number(rows.value.find((row) => row.status === status)?.count ?? 0)
  return total ? (count * 100 / total).toFixed(1) + '%' : '—'
}
watch(source, search)
onMounted(load)
onUnmounted(() => { ++generation; ++detailGeneration })
</script>
<template>
  <div class="page-heading">
    <span class="eyebrow">管理员 / 查询</span><h1>{{ titles[kind] }}</h1><p>查询真实业务记录与运行结果。</p>
  </div>
  <div class="content-card">
    <div
      v-if="kind === 'statistics'"
      class="notice-box"
    >
      取消占比 {{ share('CANCELLED') }} · 爽约占比 {{ share('NO_SHOW') }}（按所选日期内已建预约计）
    </div>
    <div class="admin-toolbar">
      <template v-if="kind === 'appointments'">
        <el-input
          v-model="outletId"
          placeholder="网点 ID"
        /><el-input
          v-model="itemId"
          placeholder="事项 ID"
        /><el-select
          v-model="status"
          clearable
          placeholder="全部状态"
        >
          <el-option
            v-for="(label, key) in appointmentStatusText"
            :key="key"
            :label="label"
            :value="key"
          />
        </el-select>
      </template>
      <template v-if="kind === 'appointments' || kind === 'statistics'">
        <input
          v-model="dateFrom"
          type="date"
          aria-label="开始日期"
        ><input
          v-model="dateTo"
          type="date"
          aria-label="结束日期"
        >
      </template>
      <template v-if="kind === 'operation-logs'">
        <el-select
          v-model="source"
          aria-label="日志来源"
        >
          <el-option
            label="预约"
            value="appointment"
          /><el-option
            label="资源"
            value="resource"
          /><el-option
            label="排队"
            value="queue"
          /><el-option
            label="账号安全"
            value="auth"
          />
        </el-select>
        <el-input
          v-model="appointmentId"
          placeholder="预约编号"
        /><el-input
          v-model="requestId"
          placeholder="请求编号"
        />
      </template>
      <el-button
        type="primary"
        :loading="loading"
        @click="search"
      >
        查询
      </el-button>
    </div>
    <StatePanel
      v-if="error"
      state="error"
      @retry="load"
    />
    <el-table
      v-else
      v-loading="loading"
      :data="rows"
    >
      <el-table-column
        v-for="(label, key) in columns"
        :key="key"
        :label="label"
        min-width="140"
      >
        <template #default="{ row }">
          {{ value(row, String(key)) }}
        </template>
      </el-table-column><el-table-column
        v-if="kind === 'appointments' || kind === 'reconciliations'"
        label="详情"
      >
        <template #default="{ row }">
          <el-button
            text
            @click="open(row)"
          >
            查看{{ kind === 'appointments' ? '时间线' : '差异' }}
          </el-button>
        </template>
      </el-table-column>
      <el-table-column
        v-if="kind === 'syncs'"
        label="处理"
      >
        <template #default="{ row }">
          <el-button
            v-if="row.status === 'NEEDS_ATTENTION'"
            text
            @click="retrySync(row)"
          >
            重试
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      v-if="total > 20 && kind !== 'statistics'"
      v-model:current-page="page"
      :total="total"
      layout="prev, pager, next"
      @current-change="load"
    />
    <template v-if="kind === 'statistics'">
      <h2>已完成办理的窗口效率</h2><el-table :data="performance">
        <el-table-column
          prop="windowId"
          label="窗口编号"
        /><el-table-column
          prop="completedCount"
          label="完成数量"
        /><el-table-column
          prop="meanWaitSeconds"
          label="平均等待（秒）"
        /><el-table-column
          prop="meanServiceSeconds"
          label="平均办理（秒）"
        />
      </el-table>
    </template>
  </div>
  <el-dialog
    v-model="detailOpen"
    title="记录详情"
    width="90%"
  >
    <StatePanel
      v-if="detailError"
      state="error"
      @retry="loadDetail"
    /><el-table
      v-else
      v-loading="detailLoading"
      :data="detail"
    >
      <el-table-column
        v-for="(label, key) in detailColumns"
        :key="key"
        :label="label"
        min-width="140"
      >
        <template #default="{ row }">
          {{ value(row, String(key)) }}
        </template>
      </el-table-column>
    </el-table><el-pagination
      v-if="detailTotal > 20"
      v-model:current-page="detailPage"
      :total="detailTotal"
      layout="prev, pager, next"
      @current-change="loadDetail"
    />
  </el-dialog>
</template>
