<script setup lang="ts">
import StatePanel from '@/components/StatePanel.vue'
defineProps<{ title: string; description: string }>()
</script>
<template>
  <div class="page-heading">
    <span class="eyebrow">管理员 / 查询</span><h1>{{ title }}</h1><p>{{ description }}</p>
  </div><div class="content-card">
    <StatePanel
      state="empty"
      title="后端查询接口尚未开放"
      :description="description"
    />
  </div>
</template>
