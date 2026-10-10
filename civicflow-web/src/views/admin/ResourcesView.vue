<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '@/api/admin'
import StatePanel from '@/components/StatePanel.vue'
import AdminResourceSelect from '@/components/AdminResourceSelect.vue'
import WindowStaffDialog from '@/components/WindowStaffDialog.vue'
import type { AdminItem, AdminResource, AdminWindow, ResourceKind, ResourceStatus } from '@/types/admin'
import { adminErrorMessage } from '@/utils/adminError'
import type { ApiError } from '@/types/api'

const props = defineProps<{ kind: ResourceKind }>()
const titles: Record<ResourceKind, string> = { outlets: '网点管理', items: '事项管理', windows: '窗口管理' }
const rows = ref<AdminResource[]>([])
const total = ref(0)
const page = ref(1)
const keyword = ref('')
const status = ref('')
const outletId = ref('')
const loading = ref(false)
const loadError = ref(false)
const busy = ref(false)
const dialog = ref(false)
const staffBinding = ref(false), staffWindow = ref<AdminWindow | null>(null)
const bindingLoading = ref(false), bindingReady = ref(false)
let bindingGeneration = 0
let savedBinding: { signature: string; key: string } | null = null
const editingId = ref('')
const formError = ref('')
const maskedContactPhone = ref<string | null>(null)
const form = reactive({ code: '', name: '', address: '', longitude: '', latitude: '', contactPhone: '', description: '', defaultDurationMinutes: 30, outletId: '', version: 0 })
const binding = ref(false)
const bindingWindow = ref<AdminWindow | null>(null)
const bindingItems = ref<AdminItem[]>([])
const bindingPage = ref(1)
const bindingTotal = ref(0)
const selectedIds = ref<string[]>([])
const bindingError = ref('')
const codeRule = /^[A-Za-z0-9][A-Za-z0-9_-]{1,63}$/

async function load() {
  loading.value = true; loadError.value = false
  try {
    const result = await adminApi.resources(props.kind, { page: page.value, size: 20, keyword: keyword.value.trim() || undefined, status: status.value || undefined, outletId: props.kind === 'windows' ? outletId.value || undefined : undefined })
    rows.value = result.items; total.value = result.total
  } catch { loadError.value = true }
  finally { loading.value = false }
}
function search() { page.value = 1; void load() }
function resetForm() { Object.assign(form, { code: '', name: '', address: '', longitude: '', latitude: '', contactPhone: '', description: '', defaultDurationMinutes: 30, outletId: '', version: 0 }); formError.value = ''; editingId.value = ''; maskedContactPhone.value = null }
function create() { resetForm(); dialog.value = true }
async function edit(row: AdminResource) {
  try {
    const latest = await adminApi.resource(props.kind, row.id)
    resetForm(); editingId.value = row.id
    Object.assign(form, { code: latest.code, name: latest.name, version: latest.version })
    if ('address' in latest) { Object.assign(form, { address: latest.address, longitude: latest.longitude?.toString() ?? '', latitude: latest.latitude?.toString() ?? '' }); maskedContactPhone.value = latest.maskedContactPhone }
    if ('description' in latest) Object.assign(form, { description: latest.description ?? '', defaultDurationMinutes: latest.defaultDurationMinutes })
    if ('outletId' in latest) form.outletId = latest.outletId
    dialog.value = true
  } catch (error) { ElMessage.error(adminErrorMessage(error)); await load() }
}
function validate(): string {
  if (!codeRule.test(form.code.trim())) return '编码需为 2–64 位字母、数字、下划线或连字符。'
  if (!form.name.trim() || form.name.length > 128) return '名称不能为空且最多 128 字。'
  if (props.kind === 'outlets') {
    if (!form.address.trim() || form.address.length > 256) return '地址不能为空且最多 256 字。'
    if (form.longitude && (Number(form.longitude) < -180 || Number(form.longitude) > 180)) return '经度需在 -180 至 180 之间。'
    if (form.latitude && (Number(form.latitude) < -90 || Number(form.latitude) > 90)) return '纬度需在 -90 至 90 之间。'
    if (form.contactPhone && (form.contactPhone.length < 6 || form.contactPhone.length > 32)) return '联系电话需为 6–32 位。'
  }
  if (props.kind === 'items' && (!Number.isInteger(form.defaultDurationMinutes) || form.defaultDurationMinutes < 1 || form.defaultDurationMinutes > 1440)) return '预计办理时长需为 1–1440 分钟。'
  if (props.kind === 'windows' && !/^[1-9][0-9]*$/.test(form.outletId)) return '请选择所属网点。'
  return ''
}
async function save() {
  formError.value = validate()
  if (formError.value || busy.value) return
  busy.value = true
  if (props.kind === 'outlets' && editingId.value && maskedContactPhone.value && !form.contactPhone.trim()) {
    try { await ElMessageBox.confirm(`当前电话 ${maskedContactPhone.value} 将被清除。确认保存？`, '确认清除联系电话', { type: 'warning' }) }
    catch { busy.value = false; return }
  }
  const common = { code: form.code.trim(), name: form.name.trim() }
  const value: Record<string, unknown> = props.kind === 'outlets'
    ? { ...common, address: form.address.trim(), longitude: form.longitude ? Number(form.longitude) : null, latitude: form.latitude ? Number(form.latitude) : null, contactPhone: form.contactPhone.trim() || null }
    : props.kind === 'items'
      ? { ...common, description: form.description.trim() || null, defaultDurationMinutes: form.defaultDurationMinutes }
      : { ...common, outletId: form.outletId.trim() }
  if (editingId.value) value.version = form.version
  try {
    if (editingId.value) await adminApi.updateResource(props.kind, editingId.value, value)
    else await adminApi.createResource(props.kind, value)
    dialog.value = false; ElMessage.success('保存成功'); await load()
  } catch (error) { formError.value = adminErrorMessage(error); if ((error as { status?: number }).status === 409) await load() }
  finally { busy.value = false }
}
async function changeStatus(row: AdminResource) {
  if (busy.value) return
  busy.value = true
  const next: ResourceStatus = row.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
  try { await ElMessageBox.confirm(`确认${next === 'ENABLED' ? '启用' : '停用'}“${row.name}”？停用可能受未来号源或业务引用限制。`, '确认状态变更', { type: 'warning' }) }
  catch { busy.value = false; return }
  try { await adminApi.resourceStatus(props.kind, row, next); await load() }
  catch (error) { ElMessage.error(adminErrorMessage(error)); await load() }
  finally { busy.value = false }
}
async function remove(row: AdminResource) {
  if (busy.value) return
  busy.value = true
  try { await ElMessageBox.confirm(`确认删除“${row.name}”？已有业务引用时服务端会拒绝。`, '确认删除', { type: 'warning' }) }
  catch { busy.value = false; return }
  try { await adminApi.deleteResource(props.kind, row); await load() }
  catch (error) { ElMessage.error(adminErrorMessage(error)); await load() }
  finally { busy.value = false }
}
async function loadBindingItems() {
  try { const result = await adminApi.resources('items', { page: bindingPage.value, size: 20, status: 'ENABLED' }); bindingItems.value = result.items as AdminItem[]; bindingTotal.value = result.total }
  catch (error) { bindingError.value = adminErrorMessage(error) }
}
async function openBinding(row: AdminResource) {
  if (!('outletId' in row)) return
  const requestGeneration = ++bindingGeneration
  savedBinding = null
  bindingWindow.value = { ...row } as AdminWindow; selectedIds.value = []; bindingPage.value = 1; bindingError.value = ''; binding.value = true
  bindingLoading.value = true; bindingReady.value = false
  try {
    const existing = await adminApi.windowItems(row.id)
    if (requestGeneration !== bindingGeneration || !binding.value || bindingWindow.value?.id !== row.id) return
    selectedIds.value = existing.itemIds; bindingWindow.value.version = existing.version
    bindingReady.value = true
    await loadBindingItems()
  } catch (error) { if (requestGeneration === bindingGeneration) bindingError.value = adminErrorMessage(error) }
  finally { if (requestGeneration === bindingGeneration) bindingLoading.value = false }
}
async function saveBinding() {
  if (!bindingWindow.value || busy.value || !bindingReady.value) return
  busy.value = true
  try { await ElMessageBox.confirm(`将“${bindingWindow.value.name}”的事项绑定更新为当前选择的 ${selectedIds.value.length} 项。确认保存？`, '确认更新事项', { type: 'warning' }) }
  catch { busy.value = false; return }
  const signature = `${bindingWindow.value.id}:${bindingWindow.value.version}:${[...selectedIds.value].sort().join(',')}`
  if (savedBinding?.signature !== signature) savedBinding = { signature, key: crypto.randomUUID() }
  try { await adminApi.bindWindowItems(bindingWindow.value, selectedIds.value, savedBinding.key); binding.value = false; await load() }
  catch (error) { bindingError.value = adminErrorMessage(error); if ((error as ApiError).status === 409) bindingReady.value = false; await load() }
  finally { busy.value = false }
}
watch(binding, value => { if (!value) bindingGeneration += 1 })
watch(() => props.kind, () => { dialog.value = false; binding.value = false; staffBinding.value = false; page.value = 1; keyword.value = ''; status.value = ''; void load() })
onMounted(() => { void load() })
</script>

<template>
  <div class="page-heading">
    <span class="eyebrow">管理员 / 资源配置</span><h1>{{ titles[kind] }}</h1><p>管理服务网点、办理事项和窗口配置。</p>
  </div>
  <div class="content-card admin-card">
    <div class="admin-toolbar">
      <el-input
        v-model="keyword"
        placeholder="搜索编码或名称"
        clearable
        @keyup.enter="search"
      /><el-select
        v-model="status"
        placeholder="全部状态"
        clearable
        @change="search"
      >
        <el-option
          label="启用"
          value="ENABLED"
        /><el-option
          label="停用"
          value="DISABLED"
        />
      </el-select><AdminResourceSelect
        v-if="kind === 'windows'"
        v-model="outletId"
        kind="outlets"
        placeholder="全部网点，可搜索名称或编码"
        :enabled-only="false"
        class="window-outlet-filter"
      /><el-button @click="search">
        查询
      </el-button><el-button
        type="primary"
        @click="create"
      >
        新增
      </el-button>
    </div>
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
      title="没有符合条件的记录"
      description="调整筛选条件或新增配置。"
    />
    <el-table
      v-else
      :data="rows"
      class="admin-table"
      stripe
    >
      <el-table-column
        prop="id"
        label="ID"
        min-width="190"
      /><el-table-column
        prop="code"
        label="编码"
        min-width="130"
      /><el-table-column
        prop="name"
        label="名称"
        min-width="180"
      /><el-table-column
        v-if="kind === 'outlets'"
        prop="address"
        label="地址"
        min-width="220"
      /><el-table-column
        v-if="kind === 'items'"
        prop="defaultDurationMinutes"
        label="办理分钟"
        width="110"
      /><el-table-column
        v-if="kind === 'windows'"
        prop="outletId"
        label="网点 ID"
        min-width="160"
      /><el-table-column
        prop="status"
        label="状态"
        width="100"
      /><el-table-column
        label="操作"
        min-width="285"
      >
        <template #default="{ row }">
          <el-button
            text
            @click="edit(row)"
          >
            编辑
          </el-button><el-button
            text
            :disabled="busy"
            @click="changeStatus(row)"
          >
            {{ row.status === 'ENABLED' ? '停用' : '启用' }}
          </el-button><el-button
            v-if="kind === 'windows'"
            text
            @click="openBinding(row)"
          >
            绑定事项
          </el-button><el-button
            v-if="kind === 'windows'"
            text
            @click="staffWindow = row; staffBinding = true"
          >
            人员授权
          </el-button><el-button
            text
            type="danger"
            :disabled="busy"
            @click="remove(row)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      v-if="total > 20"
      v-model:current-page="page"
      background
      layout="prev, pager, next"
      :total="total"
      @current-change="load"
    />
  </div>
  <el-dialog
    v-model="dialog"
    :title="`${editingId ? '编辑' : '新增'}${titles[kind]}`"
    width="min(600px, 94vw)"
    destroy-on-close
  >
    <form
      class="admin-form"
      @submit.prevent="save"
    >
      <label>编码<input
        v-model="form.code"
        maxlength="64"
        required
      ></label><label>名称<input
        v-model="form.name"
        maxlength="128"
        required
      ></label><template v-if="kind === 'outlets'">
        <label>地址<input
          v-model="form.address"
          maxlength="256"
          required
        ></label><label>经度<input
          v-model="form.longitude"
          type="number"
          min="-180"
          max="180"
          step="any"
        ></label><label>纬度<input
          v-model="form.latitude"
          type="number"
          min="-90"
          max="90"
          step="any"
        ></label><label>联系电话（编辑时留空会清除）<input
          v-model="form.contactPhone"
          autocomplete="off"
          maxlength="32"
        ></label><p
          v-if="maskedContactPhone"
          class="muted"
        >
          当前电话：{{ maskedContactPhone }}。编辑其他信息时也需重新填写完整号码，否则保存前将确认清除。
        </p>
      </template><template v-else-if="kind === 'items'">
        <label>说明<textarea
          v-model="form.description"
          maxlength="1000"
        /></label><label>预计办理分钟<input
          v-model.number="form.defaultDurationMinutes"
          type="number"
          min="1"
          max="1440"
          required
        ></label>
      </template><label
        v-else
        for="window-outlet"
      >所属网点<AdminResourceSelect
        v-model="form.outletId"
        kind="outlets"
        input-id="window-outlet"
        :disabled="busy"
      /></label><p
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
          保存
        </el-button>
      </div>
    </form>
  </el-dialog>
  <el-dialog
    v-model="binding"
    title="替换窗口事项"
    width="min(600px, 94vw)"
    destroy-on-close
  >
    <p class="admin-warning">
      已读取当前绑定。保存后，该窗口将办理下方所选事项。
    </p><p
      v-if="bindingError"
      class="admin-error"
      role="alert"
    >
      {{ bindingError }}
    </p><el-checkbox-group
      v-model="selectedIds"
      class="admin-checklist"
      :disabled="bindingLoading || !bindingReady || busy"
    >
      <el-checkbox
        v-for="item in bindingItems"
        :key="item.id"
        :value="item.id"
        :label="item.name"
      />
    </el-checkbox-group><el-pagination
      v-if="bindingTotal > 20"
      v-model:current-page="bindingPage"
      background
      layout="prev, pager, next"
      :total="bindingTotal"
      @current-change="loadBindingItems"
    /><p>已选择 {{ selectedIds.length }} 项</p><div class="admin-dialog-actions">
      <el-button @click="binding = false">
        取消
      </el-button><el-button
        type="primary"
        :loading="busy"
        :disabled="bindingLoading || !bindingReady"
        @click="saveBinding"
      >
        确认替换
      </el-button>
    </div>
  </el-dialog>
  <WindowStaffDialog
    v-model="staffBinding"
    :window="staffWindow"
    @updated="load"
  />
</template>

<style scoped>
.window-outlet-filter { width: 210px; }
@media (max-width: 760px) { .window-outlet-filter { width: 100%; } }
</style>
