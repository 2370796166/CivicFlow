<script setup lang="ts">
import { ref } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { api } from '@/api'
import { usePaged } from '@/composables/page'
import { statuses } from '@/utils/flow'
import { go } from '@/utils/navigation'
import { pending } from '@/utils/pending'
import Feedback from '@/components/Feedback.vue'
import Pager from '@/components/Pager.vue'
const status = ref(''),
  recovering = ref(false)
const choices = [
  { value: '', label: '全部' },
  ...Object.entries(statuses).map(([value, label]) => ({ value, label })),
]
const { items, page, size, total, loading, error, load } = usePaged((p) =>
  api.appointments(p, status.value),
)
onShow(() => {
  void load().then(() => {
    recovering.value = !!pending()
  })
})
onPullDownRefresh(() => {
  void load(1)
})
function change(value: string) {
  status.value = value
  void load(1)
}
</script>
<template>
  <view class="page">
    <view class="title">我的预约</view
    ><view class="subtitle">查看办理安排与预约历史</view>
    <button v-if="recovering" @tap="go('/pages/reservation/index')">
      继续查询待定预约
    </button>
    <view class="choices"
      ><view
        v-for="choice in choices"
        :key="choice.value"
        class="choice"
        :class="{ selected: status === choice.value }"
        @tap="change(choice.value)"
        >{{ choice.label }}</view
      ></view
    >
    <Feedback
      :loading="loading"
      :error="error"
      :empty="!items.length"
      empty-text="此状态暂无预约，可到首页预约办理"
      @retry="load()"
    />
    <view
      v-for="a in items"
      :key="a.appointmentId"
      class="card ticket"
      @tap="go('/pages/detail/index?id=' + a.appointmentId)"
      ><view class="row"
        ><text class="name">{{ a.itemName }}</text
        ><text class="tag">{{ statuses[a.status] }}</text></view
      ><view
        >{{ a.serviceDate }} · {{ a.slotStartTime.slice(0, 5) }}–{{
          a.slotEndTime.slice(0, 5)
        }}</view
      ><view class="muted">{{ a.outletName }}</view
      ><view class="link">查看详情 →</view></view
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
