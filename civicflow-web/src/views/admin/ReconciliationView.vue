<script setup lang="ts">
import { ElButton } from 'element-plus/es/components/button/index'
import 'element-plus/es/components/button/style/css.mjs'
import 'element-plus/es/components/message/style/css.mjs'
import 'element-plus/es/components/message-box/style/css.mjs'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus/es/components/message/index'
import { ElMessageBox } from 'element-plus/es/components/message-box/index'
import { adminApi } from '@/api/admin'
import AdminPager from '@/components/AdminPager.vue'
import StatePanel from '@/components/StatePanel.vue'
import type { AdminSlot, ReconciliationHistory, ReconciliationReport, ReconciliationQuery } from '@/types/admin'
import { adminErrorMessage } from '@/utils/adminError'
import { adminLabel, adminTime, queryValues, slotIssue, validId, validateQuery } from '@/utils/adminQueries'
const route = useRoute()
const initialId = typeof route.query.slotId === 'string' ? route.query.slotId : ''
const slotId = ref(initialId), slotRows = ref<AdminSlot[]>([]), selectedSlot = ref<AdminSlot>(), slotPage = ref(1), slotTotal = ref(0), slotLoading = ref(false), slotError = ref('')
const report = ref<ReconciliationReport | null>(null), reportFresh = ref(false), selectedHistory = ref<ReconciliationHistory | null>(null), busy = ref(false), error = ref(''), detailLoading = ref(false)
const rows = ref<ReconciliationHistory[]>([]), total = ref(0), page = ref(1), size = ref(20), loading = ref(false), historyError = ref('')
const filters = reactive({ slotId: initialId, status: '', dateFrom: '', dateTo: '' })
let applied: Partial<ReconciliationQuery> = queryValues(filters)
let historySequence = 0, slotSequence = 0, detailSequence = 0
const issue = computed(() => slotIssue(selectedSlot.value))
const canRepair = computed(() => reportFresh.value && report.value?.autoRepairable && report.value.slotId === slotId.value && !issue.value && !busy.value)
watch(slotId, () => { ++slotSequence; ++detailSequence; detailLoading.value = false; report.value = null; reportFresh.value = false; selectedHistory.value = null; selectedSlot.value = slotRows.value.find(row => row.id === slotId.value); slotError.value = ''; error.value = '' }, { flush: 'sync' })
async function loadSlots() {
  slotLoading.value = true; slotError.value = ''
  try { const result = await adminApi.slots({ page: slotPage.value, size: 20 }); slotRows.value = result.items; slotTotal.value = result.total }
  catch (cause) { slotError.value = adminErrorMessage(cause) }
  finally { slotLoading.value = false }
}
async function inspectSlot() {
  const id = slotId.value.trim(); slotId.value = id
  if (!validId(id)) { selectedSlot.value = undefined; return }
  const current = ++slotSequence
  try { const slot = await adminApi.slot(id); if (current === slotSequence && slotId.value === id) selectedSlot.value = slot }
  catch (cause) { if (current === slotSequence) slotError.value = adminErrorMessage(cause) }
}
async function loadHistory() {
  const current = ++historySequence; loading.value = true; historyError.value = ''
  try { const result = await adminApi.reconciliations({ ...applied, page: page.value, size: size.value }); if (current === historySequence) { rows.value = result.items; total.value = result.total } }
  catch (cause) { if (current === historySequence) { historyError.value = adminErrorMessage(cause); rows.value = []; total.value = 0 } }
  finally { if (current === historySequence) loading.value = false }
}
function searchHistory() { historyError.value = validateQuery(filters); if (historyError.value) return; applied = queryValues(filters); page.value = 1; void loadHistory() }
function resetHistory() { Object.assign(filters, { slotId: '', status: '', dateFrom: '', dateTo: '' }); searchHistory() }
function historyForSlot() { filters.slotId = slotId.value; searchHistory() }
async function inspectHistory(id: string) {
  const current = ++detailSequence; detailLoading.value = true; error.value = ''; report.value = null; reportFresh.value = false; selectedHistory.value = null
  try { const result = await adminApi.reconciliation(id); if (current === detailSequence) { selectedHistory.value = result; report.value = result.report } }
  catch (cause) { if (current === detailSequence) error.value = adminErrorMessage(cause) }
  finally { if (current === detailSequence) detailLoading.value = false }
}
async function run(repair: boolean) {
  slotId.value = slotId.value.trim()
  if (!validId(slotId.value)) { error.value = '请输入有效的号源 ID，或从列表选择号源。'; return }
  if (busy.value || detailLoading.value) return
  if (issue.value) { error.value = issue.value; return }
  if (repair && !canRepair.value) { error.value = '请先重新对账，只有最新报告的完整证据支持修复时才能执行。'; return }
  busy.value = true
  if (repair) {
    try { await ElMessageBox.confirm(`确认修复号源 ${slotId.value} 的库存差异？系统会再次核查证据，仅在状态未变化时执行修复。`, '确认安全修复', { type: 'warning', confirmButtonText: '确认修复' }) }
    catch { busy.value = false; return }
  }
  ++detailSequence; error.value = ''; report.value = null; reportFresh.value = false; selectedHistory.value = null
  try { report.value = await adminApi.reconcile(slotId.value, repair); reportFresh.value = true; if (repair) ElMessage.success('修复请求已完成，请查看报告。') }
  catch (cause) { error.value = adminErrorMessage(cause) }
  finally { busy.value = false; void loadHistory() }
}
onMounted(() => { void loadSlots(); void loadHistory(); if (initialId) void inspectSlot() })
</script>
<template>
  <div class="page-heading">
    <span class="eyebrow">管理中心 / 库存核查</span><h1>号源对账</h1><p>选择号源核查预约消费与剩余额度；历史报告长期保留，库存修复以最新证据为准。</p>
  </div>
  <div class="content-card admin-card admin-query-page">
    <div class="admin-section-title">
      <h2>发起对账</h2><RouterLink
        class="admin-inline-link"
        to="/admin/slots"
      >
        查看号源日历 →
      </RouterLink>
    </div>
    <div class="admin-filters">
      <label class="filter-wide">选择号源<select
        :value="slotRows.some(row => row.id === slotId) ? slotId : ''"
        :disabled="slotLoading || busy"
        @change="slotId = ($event.target as HTMLSelectElement).value; inspectSlot()"
      ><option value="">从现有号源中选择</option><option
        v-for="slot in slotRows"
        :key="slot.id"
        :value="slot.id"
      >{{ slot.serviceDate }} {{ slot.startTime.slice(0, 5) }}–{{ slot.endTime.slice(0, 5) }} · {{ adminLabel(slot.status) }} · {{ slot.id }}</option></select></label><label>号源 ID<input
        v-model="slotId"
        inputmode="numeric"
        maxlength="19"
        placeholder="也可直接输入完整 ID"
        :disabled="busy"
        @blur="inspectSlot"
        @keyup.enter="run(false)"
      ></label>
    </div>
    <div
      v-if="slotTotal > 20"
      class="admin-slot-pager"
    >
      <el-button
        :disabled="slotPage <= 1 || slotLoading"
        @click="slotPage--; loadSlots()"
      >
        上一组号源
      </el-button><span>{{ slotPage }} / {{ Math.ceil(slotTotal / 20) }}</span><el-button
        :disabled="slotPage >= Math.ceil(slotTotal / 20) || slotLoading"
        @click="slotPage++; loadSlots()"
      >
        下一组号源
      </el-button>
    </div>
    <p
      v-if="slotError"
      role="alert"
      class="admin-error"
    >
      {{ slotError }} <el-button
        text
        @click="loadSlots(); inspectSlot()"
      >
        重新加载号源
      </el-button>
    </p>
    <div
      v-if="selectedSlot"
      class="admin-slot-summary"
    >
      <strong>{{ selectedSlot.serviceDate }} · {{ selectedSlot.startTime.slice(0, 5) }}–{{ selectedSlot.endTime.slice(0, 5) }}</strong><span>{{ adminLabel(selectedSlot.status) }} / 配置额度 {{ selectedSlot.totalQuota }}</span>
    </div>
    <p
      v-if="issue"
      class="admin-warning"
    >
      {{ issue }}
    </p><p
      v-else
      class="admin-help"
    >
      只支持已放号且服务时段尚未结束的号源。选择历史号源后，可查看此前的对账记录。
    </p>
    <div class="admin-filter-actions">
      <el-button
        type="primary"
        :loading="busy"
        :disabled="!!issue || detailLoading"
        @click="run(false)"
      >
        开始对账
      </el-button><el-button
        :disabled="!validId(slotId) || busy"
        @click="historyForSlot"
      >
        查看该号源历史
      </el-button><el-button
        type="danger"
        :disabled="!canRepair"
        @click="run(true)"
      >
        安全修复
      </el-button>
    </div>
    <p
      v-if="error"
      class="admin-error"
      role="alert"
    >
      {{ error }}
    </p>
  </div>
  <div
    v-if="report || selectedHistory || detailLoading"
    class="content-card admin-card"
  >
    <StatePanel
      v-if="detailLoading"
      state="loading"
    />
    <template v-else>
      <div class="admin-section-title">
        <h2>{{ reportFresh ? '本次对账结果' : '历史报告详情' }}</h2><span
          v-if="!reportFresh"
          class="admin-history-label"
        >历史快照</span>
      </div>
      <p
        v-if="selectedHistory && !report"
        class="admin-warning"
      >
        {{ selectedHistory.failureCode ? adminErrorMessage({ code: selectedHistory.failureCode }) : selectedHistory.status === 'RUNNING' ? '此任务仍在执行中，请稍后刷新历史记录。' : '此任务没有生成报告。' }}
      </p>
      <template v-if="report">
        <div
          class="admin-result-banner"
          :data-consistent="report.classification === 'CONSISTENT'"
        >
          <strong>{{ adminLabel(report.classification) }}</strong><span>号源 {{ report.slotId }} · {{ adminTime(report.detectedAt) }}</span>
        </div>
        <div class="admin-stock-metrics">
          <div><span>预期剩余</span><strong>{{ report.expected }}</strong></div><div><span>实际剩余</span><strong>{{ report.actual ?? '未知' }}</strong></div><div><span>库存差异</span><strong>{{ report.diff ?? '未知' }}</strong></div>
        </div>
        <dl class="admin-report">
          <div
            v-for="[title, value] in [['配置总量', report.configuredTotal], ['已持久化消费', report.persistedConsumed], ['待建单预占', report.pendingReserved], ['成功补偿', report.successfulCompensations], ['待补偿', report.pendingCompensations], ['修复状态', adminLabel(report.repairStatus)]]"
            :key="title"
          >
            <dt>{{ title }}</dt><dd>{{ value }}</dd>
          </div>
        </dl><p class="admin-help">
          {{ !reportFresh ? '历史报告用于追踪当时的库存情况。执行修复前，请重新对账。' : report.autoRepairable ? '本次发现可安全修复的差异，可点击「安全修复」。' : report.classification === 'CONSISTENT' ? '库存一致，无需修复。' : '当前证据不足以自动修复，请核查预约、补偿或配置同步情况。' }}
        </p>
      </template>
    </template>
  </div>
  <div class="content-card admin-card admin-query-page">
    <div class="admin-section-title">
      <h2>对账历史</h2><span class="muted">包含手动与定时任务</span>
    </div>
    <form
      class="admin-filters"
      @submit.prevent="searchHistory"
    >
      <label>历史号源 ID<input
        v-model="filters.slotId"
        inputmode="numeric"
        maxlength="19"
      ></label><label>执行状态<select v-model="filters.status"><option value="">全部状态</option><option value="COMPLETED">已完成</option><option value="FAILED">失败</option><option value="RUNNING">执行中</option></select></label><label>检测日期起<input
        v-model="filters.dateFrom"
        type="date"
      ></label><label>检测日期止<input
        v-model="filters.dateTo"
        type="date"
      ></label><div class="admin-filter-actions">
        <el-button
          native-type="submit"
          type="primary"
          :loading="loading"
        >
          查询历史
        </el-button><el-button
          :disabled="loading"
          @click="resetHistory"
        >
          重置
        </el-button>
      </div>
    </form>
    <p
      v-if="historyError"
      class="admin-error"
      role="alert"
    >
      {{ historyError }} <el-button
        text
        @click="loadHistory"
      >
        重新加载历史
      </el-button>
    </p><StatePanel
      v-if="loading"
      state="loading"
    /><StatePanel
      v-else-if="!rows.length && !historyError"
      state="empty"
      title="暂无对账记录"
      description="选择可对账的号源并点击「开始对账」，报告会保存在这里。"
    />
    <div
      v-else-if="rows.length"
      class="admin-table-scroll"
    >
      <table class="admin-data-table">
        <thead><tr><th>检测时间</th><th>号源 ID</th><th>触发方式</th><th>执行状态</th><th>操作</th></tr></thead><tbody>
          <tr
            v-for="row in rows"
            :key="row.runId"
          >
            <td>{{ adminTime(row.startedAt) }}</td><td class="data-id">
              {{ row.slotId || '—' }}
            </td><td>{{ row.triggerType === 'ADMIN' ? '管理员手动' : '系统定时' }}</td><td>
              <span
                class="admin-status"
                :data-status="row.status"
              >{{ row.status === 'COMPLETED' ? '已完成' : adminLabel(row.status) }}</span>
            </td><td>
              <el-button
                text
                type="primary"
                :disabled="busy || detailLoading"
                @click="inspectHistory(row.runId)"
              >
                查看报告
              </el-button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <AdminPager
      v-model:page="page"
      v-model:size="size"
      :total="total"
      :loading="loading"
      @change="loadHistory"
    />
  </div>
</template>
