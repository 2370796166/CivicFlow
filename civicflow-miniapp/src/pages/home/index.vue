<script setup lang="ts">
import { ref } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { api } from '@/api'
import { usePaged } from '@/composables/page'
import { useLive } from '@/composables/live'
import { go } from '@/utils/navigation'
import { statuses } from '@/utils/flow'
import { pending } from '@/utils/pending'
import type { Appointment } from '@/types'
import Feedback from '@/components/Feedback.vue'
import Pager from '@/components/Pager.vue'
const keyword = ref(''),
  search = ref(''),
  quick = ref<Appointment | null>(null),
  recovering = ref(false)
const { items, page, total, size, loading, error, load } = usePaged((p) =>
  api.outlets(search.value, p),
)
onShow(() => {
  void load()
  recovering.value = !!pending()
})
onPullDownRefresh(() => {
  void load(1)
})
useLive(
  async (current) => {
    const results = await Promise.all(
      ['PENDING_CONFIRM', 'CONFIRMED', 'CHECKED_IN', 'SERVING'].map((s) =>
        api.appointments(1, s),
      ),
    )
    if (current()) {
      recovering.value = !!pending()
      quick.value =
        results
          .flatMap((r) => r.items)
          .sort((a, b) => a.serviceDate.localeCompare(b.serviceDate))[0] || null
    }
  },
  30000,
  () => {
    quick.value = null
    recovering.value = false
  },
)
function find() {
  search.value = keyword.value.trim()
  void load(1)
}
</script>
<template>
  <view class="page">
    <view class="eyebrow">智序 · 市民服务</view
    ><view class="title">今天要办什么？</view
    ><view class="subtitle">先选网点，再选办理事项与时间。</view>
    <view class="row"
      ><button @tap="go('/pages/notifications/index')">通知中心</button
      ><button @tap="go('/pages/help/index')">预约指南</button></view
    >
    <view v-if="recovering" class="card ticket"
      ><view class="name">有一笔预约待查询</view
      ><button @tap="go('/pages/reservation/index')">
        继续查询预约进度
      </button></view
    >
    <view
      v-if="quick"
      class="card ticket"
      @tap="go('/pages/detail/index?id=' + quick.appointmentId)"
    >
      <view class="row"
        ><text class="name">{{ quick.itemName }}</text
        ><text class="tag">{{ statuses[quick.status] }}</text></view
      >
      <view
        >{{ quick.serviceDate }} · {{ quick.slotStartTime.slice(0, 5) }}–{{
          quick.slotEndTime.slice(0, 5)
        }}</view
      ><view class="muted">{{ quick.outletName }} · 查看下一步 →</view>
    </view>
    <view class="section-title">服务网点</view>
    <view class="row"
      ><input
        v-model="keyword"
        class="field"
        style="flex: 1"
        maxlength="128"
        placeholder="搜索网点名称或地址"
        @confirm="find"
      /><button @tap="find">搜索</button></view
    >
    <Feedback
      :loading="loading"
      :error="error"
      :empty="!items.length"
      empty-text="未找到网点，请换个关键词"
      @retry="load()"
    />
    <view
      v-for="outlet in items"
      :key="outlet.id"
      class="card"
      @tap="go('/pages/outlet/index?id=' + outlet.id)"
      ><view class="row"
        ><text class="name">{{ outlet.name }}</text
        ><text class="link">选择 →</text></view
      ><view class="muted">{{ outlet.address }}</view></view
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
