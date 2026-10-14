<script setup lang="ts">
import { ElButton } from 'element-plus/es/components/button/index'
import 'element-plus/es/components/button/style/css.mjs'
import { ElSkeleton } from 'element-plus/es/components/skeleton/index'
import 'element-plus/es/components/skeleton/style/css.mjs'
withDefaults(defineProps<{ state: 'loading' | 'empty' | 'error'; title?: string; description?: string }>(), { title: '', description: '' })
defineEmits<{ retry: [] }>()
</script>
<template>
  <section
    class="state-panel"
    :aria-busy="state === 'loading'"
    :role="state === 'error' ? 'alert' : undefined"
  >
    <el-skeleton
      v-if="state === 'loading'"
      :rows="3"
      animated
    />
    <template v-else>
      <div
        class="state-icon"
        aria-hidden="true"
      >
        {{ state === 'empty' ? '○' : '!' }}
      </div>
      <h2>{{ title || (state === 'empty' ? '暂无内容' : '暂时无法加载') }}</h2>
      <p>{{ description || (state === 'empty' ? '内容准备好后会显示在这里。' : '请检查网络后重试。') }}</p>
      <el-button
        v-if="state === 'error'"
        @click="$emit('retry')"
      >
        重试
      </el-button>
    </template>
  </section>
</template>
