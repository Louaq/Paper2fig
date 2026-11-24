import { onActivated, onMounted } from 'vue'

/**
 * 首次进入页面时执行一次，之后每次从 keep-alive 缓存重新激活时再执行一次。
 *
 * 后台菜单默认开启页面缓存（keepAlive），缓存的页面再次进入时不会重新挂载，
 * 数据请求只发生在首次 onMounted，任务进度、日志、列表等会停留在上次的状态，
 * 只有手动刷新浏览器才能看到最新内容。用它替代 onMounted(load) 即可在每次进入页面时刷新。
 */
export const useRefreshOnActivate = (load: () => unknown) => {
  // 首次挂载时 onActivated 会紧跟在 onMounted 之后触发，跳过以免重复请求
  let skipActivate = true

  onMounted(() => {
    load()
  })

  onActivated(() => {
    if (skipActivate) {
      skipActivate = false
      return
    }
    load()
  })
}
