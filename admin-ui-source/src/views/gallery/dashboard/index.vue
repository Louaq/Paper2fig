<template>
  <div v-loading="loading">
    <el-row :gutter="16">
      <el-col :span="6" v-for="card in cards" :key="card.label">
        <el-card shadow="hover" class="mb-16px">
          <div class="text-12px text-gray-500">{{ card.label }}</div>
          <div class="text-28px font-bold mt-6px" :style="{ color: card.color }">{{ card.value }}</div>
        </el-card>
      </el-col>
    </el-row>

    <ContentWrap title="快捷操作">
      <el-button type="primary" @click="openCrawl">
        <Icon icon="ep:download" class="mr-5px" /> 新建采集任务
      </el-button>
      <el-button @click="router.push('/gallery/review')">
        <Icon icon="ep:finished" class="mr-5px" /> 去审核（{{ data.pendingReview || 0 }}）
      </el-button>
      <el-button @click="router.push('/gallery/publish')">
        <Icon icon="ep:upload" class="mr-5px" /> 发布管理（待发布 {{ data.approved || 0 }}）
      </el-button>
    </ContentWrap>

    <ContentWrap title="最近任务">
      <el-table :data="data.recentJobs || []" size="small">
        <el-table-column label="编号" prop="id" width="80" />
        <el-table-column label="类型" prop="kind" width="100" />
        <el-table-column label="会议" width="160">
          <template #default="{ row }">{{ row.venue }}{{ row.year ? ' ' + row.year : '' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="进度">
          <template #default="{ row }">
            <el-progress :percentage="row.progress || 0" :status="row.status === 2 ? 'success' : undefined" />
          </template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createTime" width="180" />
      </el-table>
    </ContentWrap>

    <ContentWrap title="来源分布">
      <el-table :data="venueRows" size="small">
        <el-table-column label="会议" prop="venue" width="140" />
        <el-table-column label="论文数" prop="count" />
      </el-table>
    </ContentWrap>

    <el-dialog v-model="crawlVisible" title="新建采集任务" width="460px">
      <el-form label-width="90px">
        <el-form-item label="会议">
          <el-select v-model="crawlForm.venue" class="!w-full">
            <el-option v-for="v in venues" :key="v.value" :label="v.label" :value="v.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="年份"><el-input-number v-model="crawlForm.year" :min="2020" :max="2030" /></el-form-item>
        <el-form-item label="上限"><el-input-number v-model="crawlForm.limit" :min="0" :max="5000" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="crawlVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="createCrawl">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onUnmounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createCrawlJob, getDashboard } from '@/api/gallery'
import { useRefreshOnActivate } from '@/hooks/web/useRefreshOnActivate'
import { useGalleryJobSse } from '@/hooks/web/useGalleryJobSse'

defineOptions({ name: 'GalleryDashboard' })

const router = useRouter()
const loading = ref(false)
const creating = ref(false)
const data = ref<any>({})
const crawlVisible = ref(false)
const crawlForm = reactive({ venue: 'cvpr', year: new Date().getFullYear(), limit: 20 })
const venues = [
  { value: 'iclr', label: 'ICLR' },
  { value: 'icml', label: 'ICML' },
  { value: 'neurips', label: 'NeurIPS' },
  { value: 'cvpr', label: 'CVPR' },
  { value: 'acl', label: 'ACL' },
  { value: 'aaai', label: 'AAAI' },
  { value: 'eccv', label: 'ECCV' },
  { value: 'miccai', label: 'MICCAI' },
  { value: 'ijcai', label: 'IJCAI' },
  { value: 'icme', label: 'ICME' },
  { value: 'mm', label: 'ACM MM' }
]

const cards = computed(() => [
  { label: '论文总数', value: data.value.papers ?? 0, color: '#409eff' },
  { label: '待处理（未裁剪）', value: data.value.toCrop ?? 0, color: '#e6a23c' },
  { label: '待审核', value: data.value.pendingReview ?? 0, color: '#f56c6c' },
  { label: '已通过待发布', value: data.value.approved ?? 0, color: '#67c23a' }
])

const venueRows = computed(() =>
  Object.entries(data.value.byVenue || {}).map(([venue, count]) => ({ venue, count }))
)

const statusText = (s: number) =>
  ({ 0: '等待', 1: '运行中', 2: '成功', 3: '失败', 4: '已取消' } as any)[s] || '-'
const statusType = (s: number) =>
  ({ 0: 'info', 1: 'warning', 2: 'success', 3: 'danger', 4: 'info' } as any)[s] || 'info'

const load = async () => {
  loading.value = true
  try {
    data.value = await getDashboard()
  } finally {
    loading.value = false
  }
}

// 任务状态由 SSE 异步推送，收到事件后去抖刷新，不再固定轮询
let refreshTimer: any = null
const scheduleRefresh = () => {
  if (refreshTimer) clearTimeout(refreshTimer)
  refreshTimer = setTimeout(() => {
    refreshTimer = null
    load()
  }, 400)
}
useGalleryJobSse(() => scheduleRefresh())

const openCrawl = () => {
  crawlVisible.value = true
}

const createCrawl = async () => {
  creating.value = true
  try {
    await createCrawlJob({ ...crawlForm })
    ElMessage.success('采集任务已创建，可在“论文日志”查看进度')
    crawlVisible.value = false
    await load()
  } finally {
    creating.value = false
  }
}

useRefreshOnActivate(load)
onUnmounted(() => {
  if (refreshTimer) clearTimeout(refreshTimer)
})
</script>
