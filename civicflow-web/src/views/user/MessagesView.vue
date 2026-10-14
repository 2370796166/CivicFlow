<script setup lang="ts">
import { ElButton } from 'element-plus/es/components/button/index'
import 'element-plus/es/components/button/style/css.mjs'
import { ElPagination } from 'element-plus/es/components/pagination/index'
import 'element-plus/es/components/pagination/style/css.mjs'
import { ElTag } from 'element-plus/es/components/tag/index'
import 'element-plus/es/components/tag/style/css.mjs'
import { onMounted, ref } from 'vue'
import { api } from '@/api/client'
import type { ApiResponse, Page } from '@/types/api'
import StatePanel from '@/components/StatePanel.vue'
interface Message { id: string; appointmentId: string; title: string; content: string; readAt: string | null; createdAt: string }
const rows = ref<Message[]>([]), page = ref(1), total = ref(0), loading = ref(false), error = ref(false)
async function load() {
  loading.value = true; error.value = false
  try { const response = await api.get<ApiResponse<Page<Message>>>('/user/messages', { params: { page: page.value } }); rows.value = response.data.data.items; total.value = response.data.data.total }
  catch { error.value = true }
  finally { loading.value = false }
}
async function read(row: Message) { await api.post('/user/messages/' + row.id + '/read'); row.readAt = new Date().toISOString() }
onMounted(load)
</script>
<template>
  <div class="page-heading">
    <span class="eyebrow">普通用户 / 消息</span><h1>站内消息</h1><p>预约状态与办理提醒会保存在这里。</p>
  </div>
  <div class="content-card">
    <el-button
      :loading="loading"
      @click="load"
    >
      刷新
    </el-button>
    <StatePanel
      v-if="error"
      state="error"
      @retry="load"
    /><StatePanel
      v-else-if="loading && !rows.length"
      state="loading"
    /><StatePanel
      v-else-if="!rows.length"
      state="empty"
      title="暂无消息"
    />
    <article
      v-for="row in rows"
      :key="row.id"
      class="notice-box"
    >
      <strong>{{ row.title }}</strong><el-tag v-if="!row.readAt">
        未读
      </el-tag><p>{{ row.content }}</p><small>{{ new Date(row.createdAt).toLocaleString() }}</small><div class="action-row">
        <RouterLink
          :to="'/user/appointments/' + row.appointmentId"
          class="link-text"
          @click="read(row)"
        >
          查看预约
        </RouterLink><el-button
          v-if="!row.readAt"
          text
          @click="read(row)"
        >
          标为已读
        </el-button>
      </div>
    </article>
    <el-pagination
      v-if="total > 20"
      v-model:current-page="page"
      :total="total"
      layout="prev, pager, next"
      @current-change="load"
    />
  </div>
</template>
