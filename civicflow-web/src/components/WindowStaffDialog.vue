<script setup lang="ts">
import { onUnmounted, ref, watch } from 'vue'
import { ElMessageBox } from 'element-plus'
import { adminApi } from '@/api/admin'
import { adminErrorMessage } from '@/utils/adminError'
import type { AdminUser, AdminWindow } from '@/types/admin'
import type { ApiError } from '@/types/api'

const open = defineModel<boolean>({ default: false })
const props = defineProps<{ window: AdminWindow | null }>()
const emit = defineEmits<{ updated: [] }>()
const users = ref<AdminUser[]>([]), selectedIds = ref<string[]>([]), inheritedIds = ref<string[]>([])
const keyword = ref(''), page = ref(1), total = ref(0), version = ref(0)
const loading = ref(false), busy = ref(false), ready = ref(false), error = ref('')
const names = ref(new Map<string, string>())
let generation = 0, userGeneration = 0, disposed = false
let saved: { signature: string; key: string } | null = null

async function loadUsers() {
  const requestGeneration = ++userGeneration
  try {
    const result = await adminApi.users({ page: page.value, size: 20, keyword: keyword.value.trim() || undefined })
    if (disposed || requestGeneration !== userGeneration || !open.value) return
    users.value = result.items; total.value = result.total
    for (const user of result.items) names.value.set(user.id, user.displayName)
  } catch (failure) { if (!disposed && requestGeneration === userGeneration) error.value = adminErrorMessage(failure) }
}
async function initialize() {
  if (!props.window || !open.value) return
  const requestGeneration = ++generation
  loading.value = true; ready.value = false; error.value = ''; saved = null
  try {
    const result = await adminApi.windowStaff(props.window.id)
    if (disposed || requestGeneration !== generation || !open.value) return
    selectedIds.value = [...result.staffUserIds]; inheritedIds.value = result.inheritedStaffUserIds
    version.value = result.version; ready.value = true
    await loadUsers()
  } catch (failure) { if (!disposed && requestGeneration === generation) error.value = adminErrorMessage(failure) }
  finally { if (!disposed && requestGeneration === generation) loading.value = false }
}
function search() { page.value = 1; void loadUsers() }
async function save() {
  if (!props.window || !ready.value || busy.value) return
  if (selectedIds.value.length > 100) { error.value = '一个窗口最多绑定 100 位员工。'; return }
  busy.value = true; error.value = ''
  try { await ElMessageBox.confirm(`将“${props.window.name}”的直接授权更新为所选 ${selectedIds.value.length} 位员工。确认保存？`, '确认窗口授权', { type: 'warning', confirmButtonText: '确认授权', cancelButtonText: '返回修改' }) }
  catch { busy.value = false; return }
  const signature = `${props.window.id}:${version.value}:${[...selectedIds.value].sort().join(',')}`
  if (saved?.signature !== signature) saved = { signature, key: crypto.randomUUID() }
  try {
    await adminApi.bindWindowStaff(props.window.id, selectedIds.value, version.value, saved.key)
    open.value = false; emit('updated')
  } catch (failure) {
    error.value = adminErrorMessage(failure)
    if ((failure as ApiError).status === 409) { ready.value = false; error.value = '窗口配置已变化，请重新读取授权后再修改。' }
  } finally { busy.value = false }
}
watch([open, () => props.window?.id], () => {
  generation += 1; userGeneration += 1; users.value = []; selectedIds.value = []; inheritedIds.value = []
  keyword.value = ''; page.value = 1; total.value = 0; ready.value = false
  if (open.value) void initialize()
})
onUnmounted(() => { disposed = true; generation += 1; userGeneration += 1 })
</script>

<template>
  <el-dialog
    v-model="open"
    title="窗口人员授权"
    width="min(720px, 94vw)"
    :close-on-click-modal="!busy"
    :close-on-press-escape="!busy"
    :show-close="!busy"
    destroy-on-close
  >
    <p>窗口：{{ window?.name }}。请先在用户管理中赋予员工“窗口人员”角色，再选择其窗口。</p>
    <p
      v-if="error"
      class="admin-error"
      role="alert"
    >
      {{ error }}
    </p>
    <div
      v-if="inheritedIds.length"
      class="admin-warning"
    >
      以下员工拥有该网点全部窗口的授权：{{ inheritedIds.map(id => names.get(id) || id).join('、') }}。此处仅调整当前窗口的直接授权。
    </div>
    <div class="admin-toolbar">
      <el-input
        v-model="keyword"
        placeholder="搜索员工用户名或显示名称"
        :disabled="busy"
        @keyup.enter="search"
      /><el-button
        :disabled="busy"
        @click="search"
      >
        搜索
      </el-button><el-button
        :loading="loading"
        :disabled="busy"
        @click="initialize"
      >
        重新读取授权
      </el-button>
    </div>
    <div
      class="selected-staff"
      aria-label="已授权员工"
    >
      <span
        v-for="id in selectedIds"
        :key="id"
      >{{ names.get(id) || id }}<button
        type="button"
        :disabled="busy || !ready"
        :aria-label="`移除员工 ${names.get(id) || id}`"
        @click="selectedIds = selectedIds.filter(value => value !== id)"
      >×</button></span>
    </div>
    <el-checkbox-group
      v-model="selectedIds"
      class="admin-checklist"
      :disabled="busy || loading || !ready"
    >
      <el-checkbox
        v-for="user in users"
        :key="user.id"
        :value="user.id"
        :disabled="!user.roles.includes('STAFF') || user.status !== 'ENABLED'"
      >
        {{ user.displayName }}（{{ user.username }}）{{ !user.roles.includes('STAFF') ? ' · 非窗口人员' : user.status !== 'ENABLED' ? ' · 账号不可用' : '' }}
      </el-checkbox>
    </el-checkbox-group>
    <p
      v-if="!users.length && !loading"
      class="muted"
    >
      暂无符合条件的用户。可先到用户管理创建员工并设置角色。
    </p>
    <el-pagination
      v-if="total > 20"
      v-model:current-page="page"
      layout="prev, pager, next"
      :total="total"
      :disabled="busy"
      @current-change="loadUsers"
    />
    <p>当前直接授权 {{ selectedIds.length }} 位员工</p>
    <div class="admin-dialog-actions">
      <el-button
        :disabled="busy"
        @click="open = false"
      >
        取消
      </el-button><el-button
        type="primary"
        :loading="busy"
        :disabled="loading || !ready"
        @click="save"
      >
        保存授权
      </el-button>
    </div>
  </el-dialog>
</template>
