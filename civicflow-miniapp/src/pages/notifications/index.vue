<script setup lang="ts">
import { ref } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { api } from '@/api'
import { errorText } from '@/api/client'
import { usePaged } from '@/composables/page'
import { instant, deliveryStatuses } from '@/utils/flow'
import { go } from '@/utils/navigation'
import type { Notification } from '@/types'
import Feedback from '@/components/Feedback.vue'
import Pager from '@/components/Pager.vue'
const reading = ref(''),
  actionError = ref('')
const { items, page, total, size, loading, error, load } = usePaged(
  api.notifications,
)
onShow(() => {
  void load()
})
onPullDownRefresh(() => {
  void load(1)
})
async function read(n: Notification) {
  if (reading.value || n.readAt || n.status !== 'SENT') return
  reading.value = n.id
  actionError.value = ''
  try {
    const result = await api.read(n.id)
    if (result.read) await load()
  } catch (e) {
    actionError.value = errorText(e)
  } finally {
    reading.value = ''
  }
}
</script>
<template>
  <view class="page">
    <view class="row"
      ><view class="title">通知中心</view
      ><text class="link" @tap="go('/pages/preferences/index')"
        >通知偏好</text
      ></view
    ><view class="subtitle">预约与叫号等站内通知</view>
    <Feedback
      :loading="loading"
      :error="error"
      :empty="!items.length"
      empty-text="暂无站内通知，可稍后刷新查看"
      @retry="load()"
    /><view v-if="actionError" class="error">{{ actionError }}</view>
    <view v-for="n in items" :key="n.id" class="card"
      ><view class="row"
        ><text class="name">{{ n.title }}</text
        ><text class="tag">{{
          deliveryStatuses[n.status] || n.status
        }}</text></view
      ><view class="body">{{ n.body }}</view
      ><view class="muted">{{ instant(n.sentAt || n.createdAt) }}</view
      ><button
        v-if="n.status === 'SENT' && !n.readAt"
        :disabled="!!reading"
        :loading="reading === n.id"
        @tap="read(n)"
      >
        标记已读</button
      ><view v-else-if="n.readAt" class="muted"
        >已读 · {{ instant(n.readAt) }}</view
      ><view v-if="['FAILED', 'DEAD'].includes(n.status)" class="muted"
        >可以刷新查询最新状态；如长期未恢复，请联系服务人员。</view
      ></view
    >
    <Pager
      :page="page"
      :total="total"
      :size="size"
      :loading="loading"
      @change="load"
    />
  </view>
</template>
