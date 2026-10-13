import { onScopeDispose, ref } from 'vue'
import { onShow, onHide, onUnload } from '@dcloudio/uni-app'
import { createPoller } from '@/utils/poller'
import { onSessionClear } from '@/stores/session'
export function useLive(
  work: (isCurrent: () => boolean) => Promise<boolean | void>,
  interval = 4000,
  clear: () => void = () => {},
) {
  let generation = 0
  const visible = ref(false)
  const poller = createPoller(() => {
    const run = generation
    return work(() => visible.value && run === generation)
  }, interval)
  function stop() {
    visible.value = false
    generation++
    poller.stop()
  }
  function start() {
    generation++
    visible.value = true
    poller.start()
  }
  onShow(start)
  onHide(stop)
  onUnload(stop)
  const off = onSessionClear(() => {
    stop()
    clear()
  })
  onScopeDispose(() => {
    off()
    stop()
  })
  return { start, stop, visible }
}
