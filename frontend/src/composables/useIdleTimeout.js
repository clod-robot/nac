import { onMounted, onBeforeUnmount } from 'vue'

/**
 * 空闲超时：超过 ms 无操作（鼠标/键盘/触摸/滚动）触发 onIdle 一次。
 * 用于“30 分钟无操作自动退出系统”。
 */
export function useIdleTimeout(onIdle, ms = 30 * 60 * 1000) {
  let timer = null
  const EVENTS = ['mousemove', 'mousedown', 'keydown', 'scroll', 'touchstart', 'click']

  function reset() {
    if (timer) clearTimeout(timer)
    timer = setTimeout(trigger, ms)
  }
  function trigger() {
    stop()
    onIdle && onIdle()
  }
  function stop() {
    if (timer) { clearTimeout(timer); timer = null }
    EVENTS.forEach(e => window.removeEventListener(e, reset))
  }

  onMounted(() => {
    EVENTS.forEach(e => window.addEventListener(e, reset, { passive: true }))
    reset()
  })
  onBeforeUnmount(stop)
}
