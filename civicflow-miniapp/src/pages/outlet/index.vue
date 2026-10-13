<script setup lang="ts">
import { ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { api } from '@/api'
import { errorText } from '@/api/client'
import { usePaged } from '@/composables/page'
import { go } from '@/utils/navigation'
import type { Outlet } from '@/types'
import Feedback from '@/components/Feedback.vue'
import Pager from '@/components/Pager.vue'
let id = ''
const outlet = ref<Outlet | null>(null),
  detailError = ref('')
const { items, page, total, size, loading, error, load } = usePaged((p) =>
  api.items(id, p),
)
onLoad((options) => {
  id = options?.id || ''
})
async function refresh() {
  detailError.value = ''
  try {
    outlet.value = await api.outlet(id)
  } catch (e) {
    detailError.value = errorText(e)
  }
  await load()
}
onShow(() => {
  void refresh()
})
function address() {
  const o = outlet.value
  if (!o) return
  if (
    o.latitude !== null &&
    o.longitude !== null &&
    Number.isFinite(o.latitude) &&
    Number.isFinite(o.longitude) &&
    Math.abs(o.latitude) <= 90 &&
    Math.abs(o.longitude) <= 180
  )
    uni.openLocation({
      latitude: o.latitude,
      longitude: o.longitude,
      name: o.name,
      address: o.address,
      fail: () => {
        detailError.value = '地图未打开，可复制地址后查看'
      },
    })
  else uni.setClipboardData({ data: o.address })
}
</script>
<template>
  <view class="page">
    <view v-if="outlet" class="card"
      ><view class="title">{{ outlet.name }}</view
      ><view>{{ outlet.address }}</view
      ><button @tap="address">查看位置 / 复制地址</button></view
    >
    <Feedback :error="detailError" @retry="refresh" /><view
      class="section-title"
      >选择办理事项</view
    >
    <Feedback
      :loading="loading"
      :error="error"
      :empty="!items.length"
      empty-text="此网点暂未提供可预约事项"
      @retry="load()"
    />
    <view v-for="item in items" :key="item.id" class="card"
      ><view class="name">{{ item.name }}</view
      ><view class="body muted">{{ item.description || '暂无事项说明' }}</view
      ><view class="note"
        >参考办理时长：{{ item.defaultDurationMinutes }} 分钟</view
      ><button
        class="primary"
        @tap="
          go(
            '/pages/booking/index?outletId=' +
              id +
              '&itemId=' +
              item.id +
              '&name=' +
              encodeURIComponent(item.name),
          )
        "
      >
        选择日期与时段
      </button></view
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
