<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { errorText, login } from '@/api/client'
import { go, safeReturn } from '@/utils/navigation'
const name = ref(''),
  password = ref(''),
  busy = ref(false),
  error = ref('')
let returnTo = '/pages/home/index'
onLoad((options) => {
  returnTo = safeReturn(
    options?.returnTo ? decodeURIComponent(options.returnTo) : undefined,
  )
})
async function submit() {
  if (busy.value) return
  if (!name.value.trim() || !password.value) {
    error.value = '请输入账号和密码'
    return
  }
  busy.value = true
  error.value = ''
  try {
    await login(name.value.trim(), password.value)
    password.value = ''
    go(returnTo, true)
  } catch (e) {
    error.value = errorText(e)
  } finally {
    busy.value = false
  }
}
</script>
<template>
  <view class="page">
    <view class="eyebrow">CIVICFLOW · 智序</view
    ><view class="title"
      ><view>预约好时间，</view><view>安心去办理。</view></view
    >
    <view class="subtitle">使用已有市民账号登录</view>
    <view class="section-title">账号登录</view>
    <text>用户名或手机号</text
    ><input
      v-model="name"
      class="field"
      maxlength="128"
      placeholder="请输入账号"
    />
    <text>密码</text
    ><input
      v-model="password"
      class="field"
      password
      maxlength="128"
      placeholder="请输入密码"
      @confirm="submit"
    />
    <view v-if="error" class="error">{{ error }}</view>
    <button class="primary" :loading="busy" :disabled="busy" @tap="submit">
      登录
    </button>
    <view class="note"
      >市民端支持预约、签到与排队查询。账号需要具有市民权限；账号问题请联系服务人员。</view
    >
  </view>
</template>
