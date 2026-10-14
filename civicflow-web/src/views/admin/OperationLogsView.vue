<script setup lang="ts">
import { ElButton } from 'element-plus/es/components/button/index'
import 'element-plus/es/components/button/style/css.mjs'
import { ElDrawer } from 'element-plus/es/components/drawer/index'
import 'element-plus/es/components/drawer/style/css.mjs'
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { adminApi } from '@/api/admin'
import AdminPager from '@/components/AdminPager.vue'
import StatePanel from '@/components/StatePanel.vue'
import type { LogQuery, LogSource, OperationLog } from '@/types/admin'
import { adminTime, adminLabel, logActions, queryValues, validateQuery } from '@/utils/adminQueries'
import { adminErrorMessage } from '@/utils/adminError'
const route = useRoute()
const sources: { value: LogSource; label: string; target: string }[] = [{ value: 'appointment', label: '预约办理', target: '预约 ID' }, { value: 'queue', label: '排队叫号', target: '排队票 ID' }, { value: 'resource', label: '资源配置', target: '资源 ID' }, { value: 'auth', label: '账号与权限', target: '目标用户 ID' }, { value: 'stock', label: '号源库存', target: '号源 ID' }]
const source = ref<LogSource>(sources.some(item => item.value === route.query.source) ? route.query.source as LogSource : 'appointment')
const filters = reactive({ resourceId: typeof route.query.resourceId === 'string' ? route.query.resourceId : '', actorId: '', requestId: '', action: '', dateFrom: '', dateTo: '' })
const rows = ref<OperationLog[]>([]), total = ref(0), page = ref(1), size = ref(20), loading = ref(false), error = ref('')
const detail = ref<OperationLog | null>(null), detailOpen = ref(false)
let sequence = 0
let applied: Partial<LogQuery> = queryValues(filters)
async function load() {
  const current = ++sequence; loading.value = true; error.value = ''
  try { const result = await adminApi.operationLogs(source.value, { ...applied, page: page.value, size: size.value }); if (current === sequence) { rows.value = result.items; total.value = result.total } }
  catch (cause) { if (current === sequence) { error.value = adminErrorMessage(cause); rows.value = []; total.value = 0 } }
  finally { if (current === sequence) loading.value = false }
}
function search() { error.value = validateQuery(filters); if (error.value) return; applied = queryValues(filters); page.value = 1; void load() }
function reset() { Object.assign(filters, { resourceId: '', actorId: '', requestId: '', action: '', dateFrom: '', dateTo: '' }); search() }
function switchSource(value: LogSource) { if (source.value === value) return; source.value = value; reset() }
onMounted(search)
</script>
<template>
  <div class="page-heading">
    <span class="eyebrow">管理中心 / 审计追踪</span><h1>操作日志</h1><p>按业务模块查看操作人、状态变更及请求编号，追踪每一次办理和配置变更。</p>
  </div>
  <div class="content-card admin-card admin-query-page">
    <nav
      class="admin-source-tabs"
      aria-label="日志来源"
    >
      <button
        v-for="item in sources"
        :key="item.value"
        :aria-pressed="source === item.value"
        :class="{ active: source === item.value }"
        @click="switchSource(item.value)"
      >
        {{ item.label }}
      </button>
    </nav>
    <form
      class="admin-filters"
      @submit.prevent="search"
    >
      <label>{{ sources.find(item => item.value === source)?.target }}<input
        v-model="filters.resourceId"
        inputmode="numeric"
        maxlength="19"
      ></label><label>操作人 ID<input
        v-model="filters.actorId"
        inputmode="numeric"
        maxlength="19"
      ></label><label>操作日期起<input
        v-model="filters.dateFrom"
        type="date"
      ></label><label>操作日期止<input
        v-model="filters.dateTo"
        type="date"
      ></label>
      <details class="admin-more-filters">
        <summary>按请求编号或操作类型筛选</summary><div class="admin-filters">
          <label class="filter-wide">请求编号<input
            v-model="filters.requestId"
            maxlength="128"
            placeholder="完整请求编号"
          ></label><label>操作类型<select
            v-model="filters.action"
          ><option value="">全部操作</option><option
            v-for="action in logActions[source]"
            :key="action"
            :value="action"
          >{{ adminLabel(action) }}</option></select></label>
        </div>
      </details>
      <div class="admin-filter-actions">
        <el-button
          native-type="submit"
          type="primary"
          :loading="loading"
        >
          查询日志
        </el-button><el-button
          :disabled="loading"
          @click="reset"
        >
          重置
        </el-button>
      </div>
    </form>
    <p
      v-if="error"
      class="admin-error"
      role="alert"
    >
      {{ error }} <el-button
        text
        @click="load"
      >
        重新加载
      </el-button>
    </p><StatePanel
      v-if="loading"
      state="loading"
    /><StatePanel
      v-else-if="!rows.length && !error"
      state="empty"
      title="没有匹配的操作记录"
      description="调整日期或目标 ID，也可以切换到其他业务模块。"
    />
    <div
      v-else-if="rows.length"
      class="admin-table-scroll"
    >
      <table class="admin-data-table">
        <thead><tr><th>操作时间</th><th>操作类型</th><th>操作人</th><th>操作对象</th><th>状态 / 结果</th><th>操作</th></tr></thead><tbody>
          <tr
            v-for="row in rows"
            :key="row.id"
          >
            <td>{{ adminTime(row.occurredAt) }}</td><td><strong>{{ adminLabel(row.action) }}</strong><small v-if="adminLabel(row.action) !== row.action">{{ row.action }}</small></td><td>{{ adminLabel(row.actorType) }}<small class="data-id">{{ row.actorId ?? '—' }}</small></td><td>{{ adminLabel(row.resourceType) }}<small class="data-id">{{ row.resourceId ?? '—' }}</small></td><td>
              <span v-if="row.toStatus">{{ adminLabel(row.fromStatus) }} → {{ adminLabel(row.toStatus) }}</span><span
                v-else
                class="admin-status"
                :data-status="row.outcome"
              >{{ adminLabel(row.outcome) }}</span>
            </td><td>
              <el-button
                text
                type="primary"
                @click="detail = row; detailOpen = true"
              >
                查看记录
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
      @change="load"
    />
  </div>
  <el-drawer
    v-model="detailOpen"
    title="操作记录"
    size="min(560px, 100vw)"
  >
    <template v-if="detail">
      <h2>{{ adminLabel(detail.action) }}</h2><p class="muted">
        {{ adminTime(detail.occurredAt) }} · {{ sources.find(item => item.value === source)?.label }}
      </p><dl class="admin-detail-list">
        <div
          v-for="[title, value] in [['日志 ID', detail.id], ['操作类型', detail.action], ['操作人', `${adminLabel(detail.actorType)} / ${detail.actorId ?? '—'}`], ['操作对象', `${adminLabel(detail.resourceType)} / ${detail.resourceId ?? '—'}`], ['操作结果', adminLabel(detail.outcome)], ['原状态', adminLabel(detail.fromStatus)], ['新状态', adminLabel(detail.toStatus)], ['请求编号', detail.requestId]]"
          :key="title"
        >
          <dt>{{ title }}</dt><dd>{{ value }}</dd>
        </div>
      </dl><p class="muted">
        使用请求编号关联同一次操作的服务记录。
      </p><RouterLink
        v-if="detail.source === 'appointment' && detail.resourceId"
        class="admin-inline-link"
        :to="{ path: '/admin/appointments', query: { keyword: detail.resourceId } }"
        @click="detailOpen = false"
      >
        查看关联预约 →
      </RouterLink>
    </template>
  </el-drawer>
</template>
