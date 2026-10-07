import { ref, shallowRef } from 'vue'

import { ApiError } from '@/api/errors'

/**
 * 统一的异步加载状态封装：覆盖 Loading / Empty / Error / Loaded 四种界面状态。
 *
 * 使用方式：
 *   const { data, loading, error, run } = useAsync(() => workspaceApi.summary())
 *   onMounted(run)
 */
export function useAsync<T>(loader: () => Promise<T>, options: { immediate?: boolean } = {}) {
  const data = shallowRef<T | null>(null)
  const loading = ref(false)
  const error = ref<ApiError | Error | null>(null)
  const loaded = ref(false)

  async function run(): Promise<void> {
    loading.value = true
    error.value = null
    try {
      data.value = await loader()
      loaded.value = true
    } catch (caught) {
      error.value = caught instanceof Error ? caught : new Error('加载失败')
    } finally {
      loading.value = false
    }
  }

  if (options.immediate) {
    void run()
  }

  return { data, loading, error, loaded, run }
}