<script setup lang="ts">
import { ElButton } from 'element-plus/es/components/button/index'
import 'element-plus/es/components/button/style/css.mjs'
import { computed } from 'vue'
const props = defineProps<{ page: number; size: number; total: number; loading?: boolean }>()
const emit = defineEmits<{ 'update:page': [value: number]; 'update:size': [value: number]; change: [] }>()
const pages = computed(() => Math.max(1, Math.ceil(props.total / props.size)))
function turn(value: number) { emit('update:page', value); emit('change') }
function resize(value: number) { emit('update:size', value); emit('update:page', 1); emit('change') }
</script>
<template>
  <div
    class="admin-pager"
    aria-label="分页"
  >
    <span>共 {{ total }} 条记录</span>
    <label>每页 <select
      :value="size"
      :disabled="loading"
      aria-label="每页条数"
      @change="resize(Number(($event.target as HTMLSelectElement).value))"
    ><option
      v-for="amount in [10, 20, 50]"
      :key="amount"
      :value="amount"
    >{{ amount }} 条</option></select></label>
    <div>
      <el-button
        :disabled="loading || page <= 1"
        @click="turn(page - 1)"
      >
        上一页
      </el-button><span>{{ page }} / {{ pages }}</span><el-button
        :disabled="loading || page >= pages"
        @click="turn(page + 1)"
      >
        下一页
      </el-button>
    </div>
  </div>
</template>
