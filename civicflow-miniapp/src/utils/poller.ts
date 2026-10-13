export function createPoller(
  work: () => Promise<boolean | void>,
  interval = 4000,
) {
  let timer: ReturnType<typeof setTimeout> | undefined,
    generation = 0,
    active = false
  function stop() {
    active = false
    generation++
    clearTimeout(timer)
  }
  function start() {
    stop()
    active = true
    const run = generation
    const tick = async () => {
      let done = false
      try {
        done = (await work()) === true
      } catch {
        /* page owns error display */
      }
      if (active && run === generation && !done)
        timer = setTimeout(tick, interval)
    }
    void tick()
  }
  return { start, stop, isActive: () => active }
}
