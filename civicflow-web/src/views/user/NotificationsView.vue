<script setup lang="ts">
import { ElButton } from 'element-plus/es/components/button/index'
import 'element-plus/es/components/button/style/css.mjs'
import { ElPagination } from 'element-plus/es/components/pagination/index'
import 'element-plus/es/components/pagination/style/css.mjs'
import { ElSwitch } from 'element-plus/es/components/switch/index'
import 'element-plus/es/components/switch/style/css.mjs'
import { ElTag } from 'element-plus/es/components/tag/index'
import 'element-plus/es/components/tag/style/css.mjs'
import 'element-plus/es/components/message/style/css.mjs'
import { ElLoading } from 'element-plus/es/components/loading/index'
import 'element-plus/es/components/loading/style/css.mjs'
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index'
import { notificationApi } from '@/api/notification'
import type { Notification } from '@/types/notification'

const records = ref<Notification[]>([])
const page = ref(1)
const pages = ref(0)
const loading = ref(false)
const optionalEnabled = ref(true)

async function load() {
  loading.value = true
  try {
    const result = await notificationApi.list(page.value)
    records.value = result.items
    pages.value = Math.ceil(result.total / result.size)
    optionalEnabled.value = await notificationApi.preference()
  } finally { loading.value = false }
}
async function markRead(item: Notification) {
  if (item.readAt) return
  await notificationApi.read(item.id)
  item.readAt = new Date().toISOString()
}
async function changePreference(value: boolean) {
  await notificationApi.savePreference(value)
  optionalEnabled.value = value
  ElMessage.success('通知偏好已保存')
}
onMounted(load)
const vLoading = ElLoading.directive
</script>

<template>
  <div class="page-heading">
    <span class="eyebrow">普通用户 / 我的服务</span>
    <h1>站内消息</h1>
    <p>预约、确认和叫号等必要通知始终发送；偏好仅影响可选的过号提示。</p>
  </div>
  <section class="notice-box">
    <el-switch :model-value="optionalEnabled" active-text="接收可选站内消息" @change="changePreference(Boolean($event))" />
  </section>
  <div v-loading="loading" class="user-list">
    <p v-if="!records.length && !loading">暂无消息</p>
    <article v-for="item in records" :key="item.id" class="user-list-item">
      <span class="list-heading"><strong>{{ item.title }}</strong><el-tag v-if="!item.readAt">未读</el-tag></span>
      <p>{{ item.body }}</p>
      <small>{{ new Date(item.createdAt).toLocaleString('zh-CN') }}</small>
      <el-button v-if="!item.readAt" text @click="markRead(item)">标记已读</el-button>
    </article>
  </div>
  <el-pagination v-if="pages > 1" v-model:current-page="page" :page-count="pages" layout="prev, pager, next" @current-change="load" />
</template>
