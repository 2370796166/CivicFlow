<script setup lang="ts">
import { ref, onScopeDispose } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { api } from '@/api'
import { errorText } from '@/api/client'
import Feedback from '@/components/Feedback.vue'
import { onSessionClear } from '@/stores/session'
const enabled = ref(true),
  loaded = ref(false),
  busy = ref(false),
  error = ref(''),
  saved = ref(false)
const off = onSessionClear(() => {
  enabled.value = true
  loaded.value = false
  saved.value = false
  error.value = ''
})
onScopeDispose(off)
async function load() {
  error.value = ''
  try {
    enabled.value = (await api.preference()).optionalInAppEnabled
    loaded.value = true
  } catch (e) {
    error.value = errorText(e)
  }
}
onShow(() => {
  void load()
})
function change(event: Event) {
  enabled.value = (
    event as unknown as { detail: { value: boolean } }
  ).detail.value
  saved.value = false
}
async function save() {
  if (busy.value || !loaded.value) return
  busy.value = true
  error.value = ''
  saved.value = false
  try {
    await api.savePreference(enabled.value)
    enabled.value = (await api.preference()).optionalInAppEnabled
    saved.value = true
  } catch (e) {
    error.value = errorText(e)
  } finally {
    busy.value = false
  }
}
</script>
<template>
  <view class="page">
    <view class="title">通知偏好</view
    ><Feedback :error="error" :loading="!loaded && !error" @retry="load" />
    <view class="card"
      ><view class="row"
        ><text class="name">可选站内通知</text
        ><switch
          color="#1764c0"
          :checked="enabled"
          :disabled="busy || !loaded"
          @change="change" /></view
      ><view class="muted"
        >关闭后仍会接收必要通知。可选通知范围由系统当前通知规则决定。</view
      ></view
    >
    <button
      class="primary"
      :disabled="busy || !loaded"
      :loading="busy"
      @tap="save"
    >
      保存偏好</button
    ><view v-if="saved" class="note">已保存并重新读取确认。</view>
  </view>
</template>
