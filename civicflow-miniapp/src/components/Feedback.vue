<script setup lang="ts">
defineProps<{
  loading?: boolean
  error?: string
  empty?: boolean
  emptyText?: string
}>()
defineEmits<{ retry: [] }>()
</script>
<template>
  <view v-if="error" class="error"
    ><text>{{ error }}</text
    ><button @tap="$emit('retry')">重新查询</button></view
  >
  <view v-else-if="loading" class="note">正在查询，请稍候…</view>
  <view v-else-if="empty" class="card center muted">{{
    emptyText || '暂无记录'
  }}</view>
</template>
