<template>
  <ContentWrap>
    <el-form :inline="true" :model="query" class="mb-10px">
      <el-form-item label="类型">
        <el-select v-model="query.kind" clearable class="!w-140px" placeholder="全部">
          <el-option label="采集 CRAWL" value="CRAWL" />
          <el-option label="裁剪 CROP" value="CROP" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" clearable class="!w-140px" placeholder="全部">
          <el-option v-for="s in statusList" :key="s.value" :label="s.label" :value="s.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleQuery"><Icon icon="ep:search" class="mr-5px" />搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" />重置</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="list" size="small">
      <el-table-column label="编号" prop="id" width="80" />
      <el-table-column label="类型" width="100">
        <template #default="{ row }">{{ typeText(row.kind) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="进度" min-width="220">
        <template #default="{ row }">
          <el-progress
            :percentage="row.progress || 0"
            :status="row.status === 2 ? 'success' : row.status === 3 ? 'exception' : undefined"
          />
          <div class="text-12px text-gray-500">
            完成 {{ row.done ?? 0 }} / {{ row.total ?? '-' }}，失败 {{ row.failed ?? 0 }}
          </div>
          <div v-if="row.errorMsg" class="text-12px text-red-500">{{ row.errorMsg }}</div>
        </template>
      </el-table-column>
      <el-table-column label="耗时" width="100">
        <template #default="{ row }">{{ row.durationSeconds != null ? row.durationSeconds + 's' : '-' }}</template>
      </el-table-column>
      <el-table-column label="创建时间" prop="createTime" width="170" />
      <el-table-column label="操作" width="130" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openLog(row)">日志</el-button>
          <el-button
            v-if="row.status !== 0 && row.status !== 1"
            link
            type="danger"
            @click="handleDelete(row)"
            v-hasPermi="['gallery:job:cancel']"
          >删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      class="mt-16px justify-end"
      background
      :total="total"
      v-model:current-page="query.pageNo"
      v-model:page-size="query.pageSize"
      layout="total, prev, pager, next"
      @change="load"
    />
  </ContentWrap>

  <el-dialog v-model="logVisible" title="论文日志" width="900px">
    <div class="text-12px text-gray-500 mb-8px">
      任务 #{{ current?.id }} · {{ statusText(current?.status) }}
      <el-button link type="primary" @click="refreshLog">刷新</el-button>
      <el-switch v-model="autoRefresh" active-text="自动刷新" class="ml-10px" />
    </div>
    <pre class="log-box">{{ logText || '（暂无日志）' }}</pre>
  </el-dialog>
</template>

<script setup lang="ts">
import { onUnmounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteJob, getJobLog, getJobPage } from '@/api/gallery'
import { useRefreshOnActivate } from '@/hooks/web/useRefreshOnActivate'
import { useGalleryJobSse } from '@/hooks/web/useGalleryJobSse'

defineOptions({ name: 'GalleryJob' })

const statusList = [
  { value: 0, label: '等待' }, { value: 1, label: '运行中' }, { value: 2, label: '成功' },
  { value: 3, label: '失败' }, { value: 4, label: '已取消' }
]

const loading = ref(false)
const list = ref<any[]>([])
const total = ref(0)
const query = reactive<any>({ pageNo: 1, pageSize: 10, paperOnly: true, kind: '', status: null })

const logVisible = ref(false)
const logText = ref('')
const current = ref<any>(null)
const autoRefresh = ref(true)

const typeText = (kind: string) => ({ CRAWL: '采集', CROP: '裁剪' } as any)[kind] || kind
const statusText = (s: number) =>
  ({ 0: '等待', 1: '运行中', 2: '成功', 3: '失败', 4: '已取消' } as any)[s] || '-'
const statusType = (s: number) =>
  ({ 0: 'info', 1: 'warning', 2: 'success', 3: 'danger', 4: 'info' } as any)[s] || 'info'

const load = async () => {
  loading.value = true
  try {
    const data = await getJobPage({ ...query })
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
const handleQuery = () => { query.pageNo = 1; load() }
const resetQuery = () => { Object.assign(query, { pageNo: 1, kind: '', status: null }); load() }

// SSE 推送任务变化后做一次去抖刷新，不再固定 3 秒轮询
let listRefreshTimer: any = null
let logRefreshTimer: any = null
const scheduleListRefresh = () => {
  if (listRefreshTimer) clearTimeout(listRefreshTimer)
  listRefreshTimer = setTimeout(() => {
    listRefreshTimer = null
    load()
  }, 400)
}
const scheduleLogRefresh = () => {
  if (logRefreshTimer) clearTimeout(logRefreshTimer)
  logRefreshTimer = setTimeout(() => {
    logRefreshTimer = null
    if (autoRefresh.value && logVisible.value) refreshLog()
  }, 400)
}

const handleDelete = async (row: any) => {
  await ElMessageBox.confirm(`确定删除裁剪日志 #${row.id}？日志目录会一并删除。`, '删除确认', { type: 'warning' })
  await deleteJob(row.id)
  ElMessage.success('已删除')
  if (current.value?.id === row.id) {
    logVisible.value = false
    current.value = null
  }
  await load()
}
const refreshLog = async () => {
  if (!current.value) return
  const data = await getJobLog(current.value.id, 400)
  logText.value = data.log
}
const openLog = async (row: any) => {
  current.value = row
  logText.value = ''
  logVisible.value = true
  await refreshLog()
}

// 任务状态通过 SSE 异步推送；只有收到事件时才刷新，没有定时器
useGalleryJobSse((job: any) => {
  if (!job) return
  scheduleListRefresh()
  if (logVisible.value && current.value && job.id === current.value.id) {
    current.value = { ...current.value, ...job }
    if (autoRefresh.value) scheduleLogRefresh()
  }
})

useRefreshOnActivate(load)
onUnmounted(() => {
  if (listRefreshTimer) clearTimeout(listRefreshTimer)
  if (logRefreshTimer) clearTimeout(logRefreshTimer)
})
</script>

<style scoped>
.log-box {
  max-height: 60vh;
  overflow: auto;
  background: #0b1020;
  color: #d7e1ff;
  padding: 12px;
  border-radius: 6px;
  font-size: 12px;
  line-height: 1.6;
}
</style>
