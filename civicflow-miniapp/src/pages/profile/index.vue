<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useSession } from '@/stores/session'
import { api } from '@/api'
import { logout, errorText } from '@/api/client'
import { go } from '@/utils/navigation'
const session = useSession(),
  error = ref(''),
  busy = ref(false)
onShow(async () => {
  error.value = ''
  try {
    const user = await api.me()
    if (!user.roles.includes('USER')) {
      session.clear()
      error.value = '此账号没有市民权限'
    } else session.user = user
  } catch (e) {
    error.value = errorText(e)
  }
})
async function leave() {
  if (busy.value) return
  const result = await uni.showModal({
    title: '退出登录',
    content: '退出后会清理本机登录信息与待定预约上下文。',
    confirmText: '退出登录',
  })
  if (!result.confirm) return
  busy.value = true
  try {
    await logout()
  } catch {
    uni.showToast({ title: '本机已退出，服务端注销未完成', icon: 'none' })
  } finally {
    busy.value = false
    go('/pages/login/index')
  }
}
</script>
<template>
  <view class="page">
    <view class="eyebrow">智序 · 市民账号</view
    ><view class="title">{{ session.user?.displayName || '我的' }}</view
    ><view v-if="session.user" class="muted"
      >用户编号：{{ session.user.id }}</view
    ><view v-if="error" class="error">{{ error }}</view>
    <view class="section-title">我的服务</view
    ><view class="card"
      ><view class="row link" @tap="go('/pages/appointments/index')"
        ><text>预约记录与历史</text><text>›</text></view
      ><view class="divider" /><view
        class="row link"
        @tap="go('/pages/notifications/index')"
        ><text>通知中心</text><text>›</text></view
      ><view class="divider" /><view
        class="row link"
        @tap="go('/pages/preferences/index')"
        ><text>通知偏好</text><text>›</text></view
      ><view class="divider" /><view
        class="row link"
        @tap="go('/pages/help/index')"
        ><text>使用说明</text><text>›</text></view
      ></view
    >
    <button
      v-if="session.user"
      class="danger"
      :disabled="busy"
      :loading="busy"
      @tap="leave"
    >
      退出登录</button
    ><button v-else class="primary" @tap="go('/pages/login/index')">
      登录市民账号
    </button>
  </view>
</template>
