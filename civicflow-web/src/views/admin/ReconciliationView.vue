<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '@/api/admin'
import type { ReconciliationReport } from '@/types/admin'
import { adminErrorMessage } from '@/utils/adminError'

const slotId = ref('')
const report = ref<ReconciliationReport | null>(null)
const busy = ref(false)
const error = ref('')
async function run(repair: boolean) {
  if (!/^[1-9][0-9]*$/.test(slotId.value)) { error.value = '请输入有效的号源 ID。'; return }
  if (busy.value) return
  busy.value = true
  if (repair) {
    if (!report.value?.autoRepairable || report.value.slotId !== slotId.value) { error.value = '当前报告没有可安全修复的证据，请先重新对账。'; busy.value = false; return }
    try { await ElMessageBox.confirm(`确认对号源 ${slotId.value} 执行安全修复？服务端只会在证据完整且 CAS 仍匹配时下调错误增量。`, '二次确认安全修复', { type: 'warning', confirmButtonText: '确认修复' }) }
    catch { busy.value = false; return }
  }
  error.value = ''
  try { report.value = await adminApi.reconcile(slotId.value, repair); if (repair) ElMessage.success('修复请求已完成，请查看本次报告状态。') }
  catch (cause) { error.value = adminErrorMessage(cause); report.value = null }
  finally { busy.value = false }
}
</script>

<template>
  <div class="page-heading">
    <span class="eyebrow">管理员 / 安全对账</span><h1>号源对账</h1><p>按单个号源生成最新报告；修复只在服务端证据完整时执行。</p>
  </div>
  <div class="content-card admin-card">
    <div class="admin-toolbar">
      <el-input
        v-model="slotId"
        placeholder="号源 ID"
        inputmode="numeric"
        @keyup.enter="run(false)"
      /><el-button
        type="primary"
        :loading="busy"
        @click="run(false)"
      >
        手动重跑
      </el-button><el-button
        type="danger"
        :disabled="busy || !report?.autoRepairable || report.slotId !== slotId"
        @click="run(true)"
      >
        安全修复
      </el-button>
    </div><p
      v-if="error"
      role="alert"
      class="admin-error"
    >
      {{ error }}
    </p><p class="admin-warning">
      当前后端没有对账历史列表和报告详情 GET API。这里只显示本页最近一次手动对账结果；刷新页面后不会保留。
    </p>
  </div>
  <div
    v-if="report"
    class="content-card admin-card"
  >
    <h2>本次差异详情</h2><dl class="admin-report">
      <div><dt>号源 ID</dt><dd>{{ report.slotId }}</dd></div><div><dt>分类</dt><dd>{{ report.classification }}</dd></div><div><dt>预期剩余</dt><dd>{{ report.expected }}</dd></div><div><dt>实际剩余</dt><dd>{{ report.actual ?? '未知' }}</dd></div><div><dt>差异</dt><dd>{{ report.diff ?? '无法计算' }}</dd></div><div><dt>配置总量</dt><dd>{{ report.configuredTotal }}</dd></div><div><dt>已持久化消费</dt><dd>{{ report.persistedConsumed }}</dd></div><div><dt>待建单预占</dt><dd>{{ report.pendingReserved }}</dd></div><div><dt>成功补偿</dt><dd>{{ report.successfulCompensations }}</dd></div><div><dt>待补偿</dt><dd>{{ report.pendingCompensations }}</dd></div><div><dt>修复状态</dt><dd>{{ report.repairStatus }}</dd></div><div><dt>检测时间</dt><dd>{{ report.detectedAt }}</dd></div>
    </dl>
  </div>
</template>
