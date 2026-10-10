<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { adminApi } from '@/api/admin'
import type { AdminResource } from '@/types/admin'

const props = withDefaults(defineProps<{
  kind: 'outlets' | 'items'
  inputId?: string
  placeholder?: string
  enabledOnly?: boolean
  disabled?: boolean
}>(), { inputId: undefined, placeholder: undefined, enabledOnly: true, disabled: false })
const model = defineModel<string>({ default: '' })
const emit = defineEmits<{ selected: [resource: AdminResource | null] }>()
const noun = computed(() => props.kind === 'outlets' ? '网点' : '事项')
const rows = ref<AdminResource[]>([]), selected = ref<AdminResource | null>(null)
const loading = ref(false), listError = ref(false), selectionError = ref(false)
const query = ref(''), page = ref(0), total = ref(0), failedPage = ref(1)
let listGeneration = 0, selectionGeneration = 0, disposed = false
const options = computed(() => selected.value && !rows.value.some(row => row.id === selected.value!.id)
  ? [selected.value, ...rows.value] : rows.value)
const hasMore = computed(() => page.value > 0 && page.value * 20 < total.value)
const emptyText = computed(() => query.value ? '没有匹配的结果，请换个关键词' : `暂无可选${noun.value}，请先到对应菜单新增${props.enabledOnly ? '并启用' : ''}`)
function label(row: AdminResource) { return `${row.name}（${row.code}）${row.status === 'DISABLED' ? ' · 已停用' : ''}` }
function remember(row: AdminResource | null) { selected.value = row; emit('selected', row) }

async function resolveSelection() {
  const generation = ++selectionGeneration
  const id = model.value
  selectionError.value = false
  remember(null)
  if (!id) return
  const known = rows.value.find(row => row.id === id)
  if (known) { remember(known); return }
  try {
    const row = await adminApi.resource(props.kind, id)
    if (!disposed && generation === selectionGeneration && model.value === id) remember(row)
  } catch {
    if (!disposed && generation === selectionGeneration) selectionError.value = true
  }
}
async function requestPage(nextPage: number) {
  const generation = ++listGeneration
  loading.value = true; listError.value = false; failedPage.value = nextPage
  try {
    const result = await adminApi.resources(props.kind, {
      page: nextPage, size: 20, keyword: query.value || undefined,
      status: props.enabledOnly ? 'ENABLED' : undefined,
    })
    if (disposed || generation !== listGeneration) return
    const combined = nextPage === 1 ? result.items : [...rows.value, ...result.items]
    rows.value = [...new Map(combined.map(row => [row.id, row])).values()]
    page.value = nextPage; total.value = result.total
    const current = rows.value.find(row => row.id === model.value)
    if (current) {
      selectionGeneration += 1; selectionError.value = false; remember(current)
    }
  } catch {
    if (!disposed && generation === listGeneration) listError.value = true
  } finally {
    if (!disposed && generation === listGeneration) loading.value = false
  }
}
function search(value: string) {
  const keyword = value.trim()
  if (loading.value && query.value === keyword) return
  query.value = keyword; rows.value = []; page.value = 0; total.value = 0
  void requestPage(1)
}
function update(value: string | undefined) { model.value = value || '' }
function visibleChange(visible: boolean) {
  if (visible && page.value === 0 && !loading.value && !listError.value) search(query.value)
}
watch(model, () => { void resolveSelection() }, { immediate: true })
watch(() => props.kind, () => {
  listGeneration += 1; rows.value = []; page.value = 0; total.value = 0
  query.value = ''; loading.value = false; listError.value = false
  void resolveSelection()
})
onBeforeUnmount(() => { disposed = true; listGeneration += 1; selectionGeneration += 1 })
</script>

<template>
  <div class="resource-select">
    <el-select
      :id="inputId"
      :model-value="model"
      :aria-label="noun"
      :placeholder="placeholder || `请选择${noun}，可搜索名称或编码`"
      :disabled="disabled"
      :loading="loading"
      :remote-method="search"
      :no-data-text="emptyText"
      filterable
      remote
      remote-show-suffix
      clearable
      @update:model-value="update"
      @visible-change="visibleChange"
    >
      <el-option
        v-for="row in options"
        :key="row.id"
        :value="row.id"
        :label="label(row)"
        :disabled="enabledOnly && row.status !== 'ENABLED'"
      />
      <template #empty>
        <p class="resource-select-empty">
          {{ loading ? `正在加载${noun}…` : listError ? '选项加载失败，请点击下方重试' : emptyText }}
        </p>
      </template>
      <template #footer>
        <div
          v-if="listError"
          role="alert"
          class="resource-select-footer"
        >
          <span>选项加载失败</span>
          <el-button
            text
            :disabled="loading"
            @click="requestPage(failedPage)"
          >
            重试加载
          </el-button>
        </div>
        <el-button
          v-else-if="hasMore"
          text
          :loading="loading"
          @click="requestPage(page + 1)"
        >
          加载更多
        </el-button>
      </template>
    </el-select>
    <p
      v-if="selectionError"
      role="alert"
      class="resource-selection-error"
    >
      当前{{ noun }}读取失败，请重试或重新选择。
      <el-button
        text
        @click="resolveSelection"
      >
        重新读取
      </el-button>
    </p>
  </div>
</template>

<style scoped>
.resource-select { width: 100%; min-width: 0; }
.resource-select :deep(.el-select) { width: 100%; }
.resource-select :deep(.el-select__wrapper) { min-height: 38px; }
.resource-select :deep(.el-select__input) { min-height: 0; padding: 0; border: 0; border-radius: 0; background: transparent; }
.resource-select-footer { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.resource-selection-error { margin: 4px 0 0; color: #8f3028; font-size: .85rem; }
.resource-select-empty { margin: 0; padding: 14px 18px; color: var(--muted); font-size: .85rem; }
</style>
