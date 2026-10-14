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
import type { AdminAppointment, AppointmentQuery } from '@/types/admin'
import { appointmentStatusText } from '@/utils/userFlow'
import { adminTime, adminLabel, queryValues, validateQuery } from '@/utils/adminQueries'
import { adminErrorMessage } from '@/utils/adminError'
const route = useRoute()
const filters = reactive({ keyword: typeof route.query.keyword === 'string' ? route.query.keyword : '', status: '', userId: '', outletId: '', itemId: '', slotId: typeof route.query.slotId === 'string' ? route.query.slotId : '', dateFrom: '', dateTo: '' })
const rows = ref<AdminAppointment[]>([]), total = ref(0), page = ref(1), size = ref(20)
const loading = ref(false), error = ref(''), detailError = ref(''), detailLoading = ref(false), detailOpen = ref(false)
const detail = ref<AdminAppointment | null>(null)
const names = ref<Record<string, string>>({}), nameError = ref(''), detailName = ref('')
function userName(id: string) { return names.value[id] || (nameError.value ? '姓名暂不可用' : '未填写姓名') }
let sequence = 0, detailSequence = 0
let applied: Partial<AppointmentQuery> = queryValues(filters)
async function load() {
  const current = ++sequence; loading.value = true; error.value = ''
  try {
    const result = await adminApi.appointments({ ...applied, page: page.value, size: size.value })
    if (current !== sequence) return
    let resolved: Record<string, string> = {}, failed = ''
    if (result.items.length) {
      try {
        const users = await adminApi.userNames([...new Set(result.items.map(row => row.userId))])
        resolved = Object.fromEntries(users.filter(user => user.displayName?.trim()).map(user => [user.id, user.displayName!.trim()]))
      } catch { failed = '用户姓名加载失败，请重新加载。' }
    }
    if (current === sequence) { rows.value = result.items; total.value = result.total; names.value = resolved; nameError.value = failed }
  }
  catch (cause) { if (current === sequence) { error.value = adminErrorMessage(cause); rows.value = []; total.value = 0 } }
  finally { if (current === sequence) loading.value = false }
}
function search() { error.value = validateQuery(filters); if (error.value) return; applied = queryValues(filters); page.value = 1; void load() }
function reset() { Object.assign(filters, { keyword: '', status: '', userId: '', outletId: '', itemId: '', slotId: '', dateFrom: '', dateTo: '' }); search() }
async function inspect(id: string) {
  const current = ++detailSequence; detail.value = null; detailError.value = ''; detailOpen.value = true; detailLoading.value = true
  try {
    const result = await adminApi.appointment(id)
    let resolved = names.value[result.userId]
    if (!resolved) {
      try { resolved = (await adminApi.userNames([result.userId]))[0]?.displayName?.trim() || '未填写姓名' }
      catch { resolved = '姓名暂不可用' }
    }
    if (current === detailSequence) { detail.value = result; detailName.value = resolved }
  }
  catch (cause) { if (current === detailSequence) detailError.value = adminErrorMessage(cause) }
  finally { if (current === detailSequence) detailLoading.value = false }
}
onMounted(search)
</script>
<template>
  <div class="page-heading">
    <span class="eyebrow">管理中心 / 业务记录</span><h1>预约查询</h1><p>按服务日期、预约状态或用户检索预约，查看办理进度与操作记录。</p>
  </div>
  <div class="content-card admin-card admin-query-page">
    <form
      class="admin-filters"
      @submit.prevent="search"
    >
      <label class="filter-wide">预约 / 网点 / 事项<input
        v-model="filters.keyword"
        maxlength="128"
        placeholder="预约 ID、预约编号或名称"
      ></label>
      <label>预约状态<select v-model="filters.status"><option value="">全部状态</option><option
        v-for="(text, status) in appointmentStatusText"
        :key="status"
        :value="status"
      >{{ text }}</option></select></label>
      <label>服务日期起<input
        v-model="filters.dateFrom"
        type="date"
      ></label><label>服务日期止<input
        v-model="filters.dateTo"
        type="date"
      ></label>
      <details class="admin-more-filters">
        <summary>更多筛选条件</summary><div class="admin-filters">
          <label>用户 ID<input
            v-model="filters.userId"
            inputmode="numeric"
            maxlength="19"
          ></label><label>网点 ID<input
            v-model="filters.outletId"
            inputmode="numeric"
            maxlength="19"
          ></label><label>事项 ID<input
            v-model="filters.itemId"
            inputmode="numeric"
            maxlength="19"
          ></label><label>号源 ID<input
            v-model="filters.slotId"
            inputmode="numeric"
            maxlength="19"
          ></label>
        </div>
      </details>
      <div class="admin-filter-actions">
        <el-button
          native-type="submit"
          type="primary"
          :loading="loading"
        >
          查询预约
        </el-button><el-button
          :disabled="loading"
          @click="reset"
        >
          重置
        </el-button>
      </div>
    </form>
    <p v-if="nameError && !loading && !error" class="admin-error" role="alert">{{ nameError }} <el-button text @click="load">重新加载</el-button></p>
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
    </p>
    <StatePanel
      v-if="loading"
      state="loading"
    />
    <StatePanel
      v-else-if="!rows.length && !error"
      state="empty"
      title="没有匹配的预约"
      description="调整日期或状态，也可以重置筛选查看全部记录。"
    />
    <div
      v-else-if="rows.length"
      class="admin-table-scroll"
    >
      <table class="admin-data-table">
        <thead><tr><th>预约 ID / 用户</th><th>服务网点 / 事项</th><th>服务日期 / 时段</th><th>预约状态</th><th>创建时间</th><th>操作</th></tr></thead><tbody>
          <tr
            v-for="row in rows"
            :key="row.appointment.appointmentId"
          >
            <td><span class="data-id">{{ row.appointment.appointmentId }}</span><small>{{ userName(row.userId) }}</small></td><td><strong>{{ row.appointment.outletName }}</strong><small>{{ row.appointment.itemName }}</small></td><td>{{ row.appointment.serviceDate }}<small>{{ row.appointment.slotStartTime.slice(0, 5) }}–{{ row.appointment.slotEndTime.slice(0, 5) }}</small></td><td>
              <span
                class="admin-status"
                :data-status="row.appointment.status"
              >{{ adminLabel(row.appointment.status) }}</span>
            </td><td>{{ adminTime(row.appointment.createdAt) }}</td><td>
              <el-button
                text
                type="primary"
                @click="inspect(row.appointment.appointmentId)"
              >
                查看详情
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
    title="预约详情"
    size="min(620px, 100vw)"
  >
    <StatePanel
      v-if="detailLoading"
      state="loading"
    /><p
      v-else-if="detailError"
      class="admin-error"
      role="alert"
    >
      {{ detailError }}
    </p>
    <template v-else-if="detail">
      <div class="admin-detail-heading">
        <span
          class="admin-status"
          :data-status="detail.appointment.status"
        >{{ adminLabel(detail.appointment.status) }}</span><h2>{{ detail.appointment.itemName }}</h2><p>{{ detail.appointment.outletName }} · {{ detail.appointment.serviceDate }}</p>
      </div>
      <dl class="admin-detail-list">
        <div><dt>用户姓名</dt><dd>{{ detailName }}</dd></div>
        <div><dt>预约 ID</dt><dd>{{ detail.appointment.appointmentId }}</dd></div><div><dt>预约编号</dt><dd>{{ detail.appointment.reservationId }}</dd></div><div><dt>用户 ID</dt><dd>{{ detail.userId }}</dd></div><div><dt>号源 ID</dt><dd>{{ detail.appointment.slotId }}</dd></div><div><dt>服务时段</dt><dd>{{ detail.appointment.slotStartTime }}–{{ detail.appointment.slotEndTime }}</dd></div><div><dt>排队票 ID</dt><dd>{{ detail.queueTicketId || '—' }}</dd></div>
      </dl>
      <h3>办理时间线</h3><ol class="admin-timeline">
        <template
          v-for="[title, time] in [['创建预约', detail.appointment.createdAt], ['确认预约', detail.confirmedAt], ['取消预约', detail.cancelledAt], ['预约超时', detail.expiredAt], ['现场签到', detail.checkedInAt], ['开始办理', detail.servingAt], ['完成办理', detail.completedAt], ['过号', detail.noShowAt]]"
          :key="title || ''"
        >
          <li v-if="time">
            <strong>{{ title }}</strong><span>{{ adminTime(time) }}</span>
          </li>
        </template>
      </ol>
      <RouterLink
        class="admin-inline-link"
        :to="{ path: '/admin/operation-logs', query: { source: 'appointment', resourceId: detail.appointment.appointmentId } }"
        @click="detailOpen = false"
      >
        查看此预约的操作日志 →
      </RouterLink>
    </template>
  </el-drawer>
</template>
