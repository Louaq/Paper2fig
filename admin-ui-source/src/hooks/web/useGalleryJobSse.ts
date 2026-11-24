import { onActivated, onDeactivated, onMounted, onUnmounted } from 'vue'
import { EventStreamContentType, fetchEventSource } from '@microsoft/fetch-event-source'
import { getSseConfig } from '@/api/infra/sse'
import { getAccessToken } from '@/utils/auth'

/**
 * 订阅画廊任务状态（SSE）。
 *
 * 任务进度/状态由后端主动推送，页面只在收到事件后做一次去抖刷新，
 * 不再使用 setInterval 定时轮询。SSE 未开启或连接失败时静默降级为手动刷新。
 */
export const useGalleryJobSse = (onJobUpdate: (job: any) => void) => {
  let controller: AbortController | null = null
  let active = false
  let starting = false
  const clientId = `gallery-${Math.random().toString(36).slice(2, 10)}`

  const stop = () => {
    active = false
    starting = false
    if (controller) {
      controller.abort()
      controller = null
    }
  }

  const start = async () => {
    active = true
    if (controller || starting) return
    starting = true
    try {
      const config = await getSseConfig()
      if (!active || !config?.sseEnabled) return
      const path = config.ssePath || '/infra/sse'
      const base = import.meta.env.VITE_BASE_URL || ''
      const localController = new AbortController()
      controller = localController
      const url = `${base}${path}/connect?clientId=${encodeURIComponent(clientId)}`
      fetchEventSource(url, {
        headers: { Authorization: `Bearer ${getAccessToken()}` },
        signal: localController.signal,
        openWhenHidden: true,
        onopen: async (response) => {
          if (!response.ok) throw new Error(`SSE HTTP ${response.status}`)
          const type = (response.headers.get('content-type') || '').toLowerCase()
          if (!type.startsWith(EventStreamContentType)) throw new Error('SSE content-type invalid')
        },
        onmessage: (event) => {
          if (event.event !== 'gallery-job-update' || !event.data) return
          try {
            const payload = JSON.parse(event.data)
            // SSE 框架会把消息包装成 { type, content, eventId, timestamp }，content 是业务 JSON 字符串
            const job = payload && typeof payload.content === 'string'
              ? JSON.parse(payload.content)
              : payload
            onJobUpdate(job)
          } catch {
            // 忽略无法解析的事件
          }
        },
        // 返回后由 fetch-event-source 按服务端下发的 retry（默认 5s）自动重连
        onerror: () => undefined
      })
        .catch(() => undefined)
        .finally(() => {
          if (controller === localController) controller = null
        })
    } catch {
      // SSE 未开启或连接失败：保留手动刷新，不影响页面使用
    } finally {
      starting = false
    }
  }

  onMounted(start)
  onActivated(start)
  onDeactivated(stop)
  onUnmounted(stop)
}
