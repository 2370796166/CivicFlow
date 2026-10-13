<script setup lang="ts">
import { onLaunch } from '@dcloudio/uni-app'
import { restoreSession } from './api/client'
onLaunch(() => {
  void restoreSession().catch(() => {
    /* 页面显示恢复错误并提供登录 */
  })
})
</script>
<style>
@import './style.css';
</style>
