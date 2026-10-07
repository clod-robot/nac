import { ref, computed, onMounted, onBeforeUnmount } from 'vue'

/**
 * 响应式断点：监听 resize 更新，供组件消费。
 * 断点：isMobile < 768，isNarrow < 992。
 */
export function useBreakpoints() {
  const width = ref(typeof window !== 'undefined' ? window.innerWidth : 1920)

  const onResize = () => { width.value = window.innerWidth }

  onMounted(() => {
    width.value = window.innerWidth
    window.addEventListener('resize', onResize)
  })
  onBeforeUnmount(() => {
    window.removeEventListener('resize', onResize)
  })

  const isMobile = computed(() => width.value < 768)
  const isNarrow = computed(() => width.value < 992)

  return { width, isMobile, isNarrow }
}
