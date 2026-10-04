<script setup lang="ts">
import StatePanel from '@/components/StatePanel.vue'
defineProps<{ title: string; subtitle: string }>()
</script>
<template><div class="page-heading"><span class="eyebrow">智序 / 工作空间</span><h1>{{ title }}</h1><p>{{ subtitle }}</p></div><div class="content-card"><div class="card-topline"><strong>页面概览</strong><span>基础工程</span></div><StatePanel state="empty" title="页面即将开放" description="当前已完成身份、导航与通用状态能力；业务功能将在后续阶段接入。" /></div></template>
