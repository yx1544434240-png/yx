import { onBeforeUnmount, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { createPagedQuery } from './paged-query-core.js'

// 页面用法：const list = usePagedQuery({ fetcher: (params, { signal }) => request.get(url, { params, signal }), defaults: { status: '' } })
export function usePagedQuery({ syncRoute = true, immediate = true, ...options }) {
  const route = useRoute()
  const router = useRouter()
  const list = createPagedQuery({
    ...options,
    readQuery: () => route.query,
    writeQuery: syncRoute ? query => router.replace({ query }) : undefined
  })
  if (syncRoute) {
    watch(
      () => route.query,
      query => {
        list.syncFromQuery(query)
      }
    )
  }
  if (immediate) onMounted(list.load)
  onBeforeUnmount(list.dispose)
  return list
}
