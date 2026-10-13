import { ref, shallowRef, onScopeDispose } from 'vue'
import { onHide, onUnload } from '@dcloudio/uni-app'
import { errorText } from '@/api/client'
import { onSessionClear } from '@/stores/session'
import type { Page } from '@/types'
export function usePaged<T>(fetcher: (page: number) => Promise<Page<T>>) {
  const items = shallowRef<T[]>([]),
    page = ref(1),
    total = ref(0),
    size = ref(20),
    loading = ref(false),
    error = ref('')
  let generation = 0
  async function load(next = page.value) {
    const run = ++generation
    loading.value = true
    error.value = ''
    try {
      const data = await fetcher(next)
      if (run !== generation) return
      items.value = data.items
      page.value = data.page
      total.value = data.total
      size.value = data.size
    } catch (e) {
      if (run === generation) error.value = errorText(e)
    } finally {
      if (run === generation) {
        loading.value = false
        uni.stopPullDownRefresh()
      }
    }
  }
  function invalidate() {
    generation++
    loading.value = false
  }
  onHide(invalidate)
  onUnload(invalidate)
  const off = onSessionClear(() => {
    invalidate()
    items.value = []
    total.value = 0
  })
  onScopeDispose(off)
  return { items, page, total, size, loading, error, load, invalidate }
}
