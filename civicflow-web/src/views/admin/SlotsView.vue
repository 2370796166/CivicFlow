<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '@/api/admin'
import StatePanel from '@/components/StatePanel.vue'
import AdminResourceSelect from '@/components/AdminResourceSelect.vue'
import type { AdminResource, AdminSlot, SlotBatchResult, SlotStatus } from '@/types/admin'
import { adminErrorMessage } from '@/utils/adminError'
import { chinaInstant, chinaLocal, dayCount, validateSlotTimes } from '@/utils/slotForm'

const rows = ref<AdminSlot[]>([])
const total = ref(0), page = ref(1)
const outletId = ref(''), itemId = ref(''), date = ref(''), status = ref('')
const calendarDate = ref(new Date())
const loading = ref(false), loadError = ref(false), busy = ref(false)
const dialog = ref(false), mode = ref<'single' | 'batch'>('single'), editId = ref(''), formError = ref('')
const batchResult = ref<SlotBatchResult | null>(null)
const formOutlet = ref<AdminResource | null>(null), formItem = ref<AdminResource | null>(null)
const outletName = () => formOutlet.value?.name || '未选择网点'
const itemName = () => formItem.value?.name || '未选择事项'
const form = reactive({ outletId: '', itemId: '', serviceDate: '', endDate: '', startTime: '09:00', endTime: '09:30', totalQuota: 20, releaseAt: '', releaseDaysBefore: 7, releaseTime: '08:00', checkInStart: '08:30', checkInEnd: '09:30', status: 'DRAFT' as 'DRAFT' | 'SCHEDULED', version: 0 })
const transitions: Record<SlotStatus, SlotStatus[]> = { DRAFT: ['SCHEDULED', 'OPEN', 'CLOSED'], SCHEDULED: ['OPEN', 'SUSPENDED', 'CLOSED'], OPEN: ['SUSPENDED', 'CLOSED'], SUSPENDED: ['SCHEDULED', 'OPEN', 'CLOSED'], CLOSED: [] }
const idRule = /^[1-9][0-9]*$/
async function load() {
  loading.value = true; loadError.value = false
  try { const result = await adminApi.slots({ page: page.value, size: 20, outletId: outletId.value || undefined, itemId: itemId.value || undefined, dateFrom: date.value || undefined, dateTo: date.value || undefined, status: status.value || undefined }); rows.value = result.items; total.value = result.total }
  catch { loadError.value = true }
  finally { loading.value = false }
}
function search() { page.value = 1; void load() }
function pickCalendar(value: Date) { date.value = `${value.getFullYear()}-${String(value.getMonth() + 1).padStart(2, '0')}-${String(value.getDate()).padStart(2, '0')}`; search() }
function open(modeValue: 'single' | 'batch') {
  Object.assign(form, { outletId: outletId.value, itemId: itemId.value, serviceDate: date.value, endDate: date.value, startTime: '09:00', endTime: '09:30', totalQuota: 20, releaseAt: '', releaseDaysBefore: 7, releaseTime: '08:00', checkInStart: '08:30', checkInEnd: '09:30', status: 'DRAFT', version: 0 })
  mode.value = modeValue; editId.value = ''; formError.value = ''; dialog.value = true
}
async function edit(row: AdminSlot) {
  if (row.status !== 'DRAFT') return
  try {
    const latest = await adminApi.slot(row.id)
    open('single'); editId.value = row.id
    Object.assign(form, { outletId: latest.outletId, itemId: latest.itemId, serviceDate: latest.serviceDate, startTime: latest.startTime.slice(0, 5), endTime: latest.endTime.slice(0, 5), totalQuota: latest.totalQuota, releaseAt: chinaLocal(latest.releaseAt), checkInStart: chinaLocal(latest.checkInStart).slice(11), checkInEnd: chinaLocal(latest.checkInEnd).slice(11), version: latest.version, status: 'DRAFT' })
  } catch (error) { ElMessage.error(adminErrorMessage(error)); await load() }
}
function validate(): string {
  if (!idRule.test(form.outletId) || !idRule.test(form.itemId)) return '请选择网点和事项。'
  if (!form.serviceDate) return '请选择服务日期。'
  const timeError = validateSlotTimes(form.startTime, form.endTime, form.checkInStart, form.checkInEnd)
  if (timeError) return timeError
  if (!Number.isInteger(form.totalQuota) || form.totalQuota < 0 || form.totalQuota > 1000000) return '额度需为 0–1000000 的整数。'
  if (mode.value === 'batch') {
    const days = dayCount(form.serviceDate, form.endDate)
    if (days < 1 || days > 31) return '批量日期范围需为 1–31 天。'
    if (form.releaseDaysBefore < 0 || form.releaseDaysBefore > 365 || !form.releaseTime) return '请填写有效的放号提前天数与时间。'
    if (form.releaseDaysBefore === 0 && form.releaseTime >= form.startTime) return '当天放号时间必须早于时段开始。'
  } else if (!form.releaseAt || !Number.isFinite(Date.parse(`${form.releaseAt}:00+08:00`))) return '请填写有效的放号时间。'
  else if (Date.parse(`${form.releaseAt}:00+08:00`) >= Date.parse(`${form.serviceDate}T${form.startTime}:00+08:00`)) return '放号时间必须早于服务时段开始。'
  return ''
}
function value(): Record<string, unknown> {
  const common = { outletId: form.outletId, itemId: form.itemId, startTime: form.startTime, endTime: form.endTime, totalQuota: form.totalQuota, checkInStart: mode.value === 'batch' ? form.checkInStart : chinaInstant(`${form.serviceDate}T${form.checkInStart}`), checkInEnd: mode.value === 'batch' ? form.checkInEnd : chinaInstant(`${form.serviceDate}T${form.checkInEnd}`) }
  if (mode.value === 'batch') return { ...common, startDate: form.serviceDate, endDate: form.endDate, releaseDaysBefore: form.releaseDaysBefore, releaseTime: form.releaseTime, status: form.status }
  return { ...common, serviceDate: form.serviceDate, releaseAt: chinaInstant(form.releaseAt), ...(editId.value ? { version: form.version } : { status: form.status }) }
}
async function save() {
  formError.value = validate()
  if (formError.value || busy.value) return
  busy.value = true
  if (mode.value === 'batch') {
    const count = dayCount(form.serviceDate, form.endDate)
    try { await ElMessageBox.confirm(`将为“${outletName()}”的“${itemName()}”，在 ${form.serviceDate} 至 ${form.endDate} 的 ${count} 个服务日生成 ${form.startTime}–${form.endTime} 时段；完全重复会跳过，冲突会逐日返回。确认提交？`, '确认批量生成', { type: 'warning' }) }
    catch { busy.value = false; return }
  }
  try {
    if (mode.value === 'batch') batchResult.value = await adminApi.batchSlots(value())
    else if (editId.value) await adminApi.updateSlot(editId.value, value())
    else await adminApi.createSlot(value())
    dialog.value = false; await load()
  } catch (error) { formError.value = adminErrorMessage(error); if ((error as { status?: number }).status === 409) await load() }
  finally { busy.value = false }
}
async function changeStatus(row: AdminSlot, next: SlotStatus) {
  if (busy.value) return
  busy.value = true
  try { await ElMessageBox.confirm(`确认将 ${row.serviceDate} ${row.startTime.slice(0, 5)} 的号源从 ${row.status} 改为 ${next}？此操作可能影响预约。`, '确认号源状态', { type: 'warning' }) }
  catch { busy.value = false; return }
  try { await adminApi.slotStatus(row, next); await load() }
  catch (error) { ElMessage.error(adminErrorMessage(error)); await load() }
  finally { busy.value = false }
}
async function adjustQuota(row: AdminSlot) {
  if (busy.value) return
  busy.value = true
  let entered: string
  try { const answer = await ElMessageBox.prompt('请输入新的总额度（0–1000000）。降低额度须有已同步的消费证据。', '调整号源额度', { inputValue: String(row.totalQuota), inputPattern: /^(0|[1-9][0-9]{0,6})$/, inputErrorMessage: '请输入有效整数' }); entered = answer.value }
  catch { busy.value = false; return }
  const amount = Number(entered)
  if (amount > 1000000) { ElMessage.error('额度最多 1000000'); busy.value = false; return }
  try { await ElMessageBox.confirm(`确认将总额度从 ${row.totalQuota} 调整为 ${amount}？已消费预约不会因此取消。`, '二次确认额度', { type: 'warning' }) }
  catch { busy.value = false; return }
  try { await adminApi.slotQuota(row, amount); await load() }
  catch (error) { ElMessage.error(adminErrorMessage(error)); await load() }
  finally { busy.value = false }
}
onMounted(() => { void load() })
</script>

<template>
  <div class="page-heading">
    <span class="eyebrow">管理员 / 号源配置</span><h1>时间段与号源日历</h1><p>按服务日期查看时段；配置与实际库存由服务端分别裁决。</p>
  </div>
  <div class="content-card admin-card">
    <div class="admin-toolbar">
      <AdminResourceSelect
        v-model="outletId"
        kind="outlets"
        placeholder="全部网点，可搜索名称或编码"
        :enabled-only="false"
        class="slot-resource-filter"
      /><AdminResourceSelect
        v-model="itemId"
        kind="items"
        placeholder="全部事项，可搜索名称或编码"
        :enabled-only="false"
        class="slot-resource-filter"
      /><input
        v-model="date"
        type="date"
        aria-label="服务日期"
      ><el-select
        v-model="status"
        placeholder="全部状态"
        clearable
      >
        <el-option
          v-for="option in ['DRAFT','SCHEDULED','OPEN','SUSPENDED','CLOSED']"
          :key="option"
          :label="option"
          :value="option"
        />
      </el-select><el-button @click="search">
        查询
      </el-button><el-button @click="open('single')">
        单日新增
      </el-button><el-button
        type="primary"
        @click="open('batch')"
      >
        批量生成
      </el-button>
    </div><details class="admin-calendar-wrap">
      <summary>展开日期日历</summary><p class="muted">
        选择日期后查询当日时段；日历未标记数量，不代表该日没有号源。
      </p><el-calendar
        v-model="calendarDate"
        @input="pickCalendar"
      />
    </details>
    <StatePanel
      v-if="loading"
      state="loading"
    /><StatePanel
      v-else-if="loadError"
      state="error"
      @retry="load"
    /><StatePanel
      v-else-if="!rows.length"
      state="empty"
      title="这一天暂无号源"
      description="调整日期或新建时段。"
    />
    <el-table
      v-else
      :data="rows"
      stripe
      class="admin-table"
    >
      <el-table-column
        prop="serviceDate"
        label="服务日期"
        width="120"
      /><el-table-column
        label="时段"
        width="150"
      >
        <template #default="{ row }">
          {{ row.startTime.slice(0, 5) }}–{{ row.endTime.slice(0, 5) }}
        </template>
      </el-table-column><el-table-column
        prop="outletId"
        label="网点 ID"
        min-width="145"
      /><el-table-column
        prop="itemId"
        label="事项 ID"
        min-width="145"
      /><el-table-column
        prop="totalQuota"
        label="总额度"
        width="90"
      /><el-table-column
        prop="status"
        label="状态"
        width="115"
      /><el-table-column
        label="操作"
        min-width="280"
      >
        <template #default="{ row }">
          <el-button
            v-if="row.status === 'DRAFT'"
            text
            @click="edit(row)"
          >
            编辑
          </el-button><el-dropdown
            v-if="transitions[row.status as SlotStatus].length"
            trigger="click"
            @command="(next: SlotStatus) => changeStatus(row, next)"
          >
            <el-button
              text
              :disabled="busy"
            >
              启停/关闭
            </el-button><template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item
                  v-for="next in transitions[row.status as SlotStatus]"
                  :key="next"
                  :command="next"
                >
                  {{ next }}
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown><el-button
            text
            :disabled="busy"
            @click="adjustQuota(row)"
          >
            调整额度
          </el-button>
        </template>
      </el-table-column>
    </el-table><el-pagination
      v-if="total > 20"
      v-model:current-page="page"
      background
      layout="prev, pager, next"
      :total="total"
      @current-change="load"
    />
  </div>
  <div
    v-if="batchResult"
    class="content-card admin-card"
  >
    <h2>批量生成结果</h2><p>新增 {{ batchResult.created }}，跳过 {{ batchResult.skipped }}，失败 {{ batchResult.failed.length }}。</p><StatePanel
      v-if="!batchResult.failed.length"
      state="empty"
      title="没有逐日失败"
    /><ul
      v-else
      class="admin-failures"
    >
      <li
        v-for="failure in batchResult.failed"
        :key="`${failure.serviceDate}:${failure.code}`"
      >
        <strong>{{ failure.serviceDate }}</strong><span>{{ failure.code }} · {{ failure.message }}</span>
      </li>
    </ul>
  </div>
  <el-dialog
    v-model="dialog"
    :title="mode === 'batch' ? '批量生成时段' : editId ? '编辑草稿时段' : '单日新增时段'"
    width="min(650px, 94vw)"
    destroy-on-close
  >
    <form
      class="admin-form"
      @submit.prevent="save"
    >
      <label for="slot-outlet">网点<AdminResourceSelect
        v-model="form.outletId"
        kind="outlets"
        input-id="slot-outlet"
        :disabled="busy"
        @selected="formOutlet = $event"
      /></label><label for="slot-item">事项<AdminResourceSelect
        v-model="form.itemId"
        kind="items"
        input-id="slot-item"
        :disabled="busy"
        @selected="formItem = $event"
      /></label><label>{{ mode === 'batch' ? '开始日期' : '服务日期' }}<input
        v-model="form.serviceDate"
        type="date"
        required
      ></label><label v-if="mode === 'batch'">结束日期<input
        v-model="form.endDate"
        type="date"
        required
      ></label><div class="admin-form-pair">
        <label>开始时间<input
          v-model="form.startTime"
          type="time"
          required
        ></label><label>结束时间<input
          v-model="form.endTime"
          type="time"
          required
        ></label>
      </div><label>总额度<input
        v-model.number="form.totalQuota"
        type="number"
        min="0"
        max="1000000"
        required
      ></label><template v-if="mode === 'batch'">
        <label>提前放号天数<input
          v-model.number="form.releaseDaysBefore"
          type="number"
          min="0"
          max="365"
          required
        ></label><label>放号时间<input
          v-model="form.releaseTime"
          type="time"
          required
        ></label>
      </template><label v-else>放号时间（北京时间）<input
        v-model="form.releaseAt"
        type="datetime-local"
        required
      ></label><div class="admin-form-pair">
        <label>签到开始<input
          v-model="form.checkInStart"
          type="time"
          required
        ></label><label>签到结束<input
          v-model="form.checkInEnd"
          type="time"
          required
        ></label>
      </div><label v-if="!editId">初始状态<select v-model="form.status"><option value="DRAFT">草稿</option><option value="SCHEDULED">已排期</option></select></label><p
        v-if="mode === 'batch' && form.serviceDate && form.endDate"
        class="admin-warning"
      >
        影响网点“{{ outletName() }}”、事项“{{ itemName() }}”，{{ form.serviceDate }} 至 {{ form.endDate }} 共 {{ dayCount(form.serviceDate, form.endDate) }} 个服务日，每日 {{ form.startTime }}–{{ form.endTime }}，额度 {{ form.totalQuota }}；最终逐项结果以服务端为准。
      </p><p
        v-if="formError"
        class="admin-error"
        role="alert"
      >
        {{ formError }}
      </p><div class="admin-dialog-actions">
        <el-button @click="dialog = false">
          取消
        </el-button><el-button
          native-type="submit"
          type="primary"
          :loading="busy"
        >
          {{ mode === 'batch' ? '预览并提交' : '保存' }}
        </el-button>
      </div>
    </form>
  </el-dialog>
</template>

<style scoped>
.slot-resource-filter { width: 210px; }
@media (max-width: 760px) { .slot-resource-filter { width: 100%; } }
</style>
