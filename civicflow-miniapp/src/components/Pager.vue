<script setup lang="ts">
defineProps<{ page: number; total: number; size: number; loading?: boolean }>()
defineEmits<{ change: [page: number] }>()
</script>
<template>
  <view v-if="total > size" class="row pager">
    <button :disabled="loading || page <= 1" @tap="$emit('change', page - 1)">
      上一页
    </button>
    <text class="muted">{{ page }} / {{ Math.ceil(total / size) }}</text>
    <button
      :disabled="loading || page * size >= total"
      @tap="$emit('change', page + 1)"
    >
      下一页
    </button>
  </view>
</template>
