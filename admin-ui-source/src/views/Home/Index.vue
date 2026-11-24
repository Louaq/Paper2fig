<template>
  <div v-loading="loading">
    <!-- 关键指标 -->
    <el-row :gutter="16">
      <el-col :xs="12" :sm="6" v-for="card in cards" :key="card.label">
        <el-card shadow="never" class="mb-16px">
          <div class="flex items-center justify-between">
            <div>
              <div class="text-12px text-gray-500">{{ card.label }}</div>
              <div class="text-28px font-bold mt-6px" :style="{ color: card.color }">{{ card.value }}</div>
            </div>
            <Icon :icon="card.icon" :size="34" :color="card.color" class="opacity-60" />
          </div>
          <div class="text-12px text-gray-400 mt-6px">{{ card.hint }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <!-- 站点信息 -->
      <el-col :xs="24" :md="12">
        <el-card shadow="never" class="mb-16px h-full">
          <template #header><span class="font-bold">静态站</span></template>
          <el-descriptions :column="1" size="small" border>
            <el-descriptions-item label="收录主图">{{ site.figures ?? '-' }} 张</el-descriptions-item>
            <el-descriptions-item label="索引更新">{{ site.updatedAt || '-' }}</el-descriptions-item>
            <el-descriptions-item label="最近发布">
              {{ lastPublish ? lastPublish.version + ' · +' + (lastPublish.added ?? 0) : '暂无' }}
            </el-descriptions-item>
            <el-descriptions-item label="站点目录">
              <span class="text-12px break-all">{{ site.dir || '-' }}</span>
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>

      <!-- 系统信息 -->
      <el-col :xs="24" :md="12">
        <el-card shadow="never" class="mb-16px h-full">
          <template #header><span class="font-bold">运行环境</span></template>
          <el-descriptions :column="1" size="small" border>
            <el-descriptions-item label="Java">{{ system.java || '-' }}</el-descriptions-item>
            <el-descriptions-item label="操作系统">{{ system.os || '-' }}</el-descriptions-item>
            <el-descriptions-item label="CPU / 内存">
              {{ system.cores || '-' }} 核 · {{ system.heapUsedMb }}/{{ system.heapMaxMb }} MB
            </el-descriptions-item>
            <el-descriptions-item label="已运行">{{ system.uptime || '-' }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :xs="24" :md="16">
        <el-card shadow="never">
          <template #header><span class="font-bold">最近任务</span></template>
          <el-table :data="stats.recentJobs || []" size="small" empty-text="还没有任务，去「管线任务」新建一个">
            <el-table-column label="编号" prop="id" width="70" />
            <el-table-column label="类型" prop="kind" width="90" />
            <el-table-column label="会议" width="130">
              <template #default="{ row }">{{ row.venue ? row.venue.toUpperCase() + ' ' + (row.year || '') : '-' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="进度">
              <template #default="{ row }">
                <el-progress :percentage="row.progress || 0" :status="row.status === 2 ? 'success' : undefined" />
              </template>
            </el-table-column>
            <el-table-column label="创建时间" prop="createTime" width="165" />
          </el-table>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="8">
        <el-card shadow="never">
          <template #header><span class="font-bold">论文来源</span></template>
          <el-table :data="venueRows" size="small" empty-text="论文库为空">
            <el-table-column label="会议" prop="venue" />
            <el-table-column label="数量" prop="count" width="90" align="right" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getDashboard } from '@/api/gallery'

defineOptions({ name: 'Index' })

const loading = ref(false)
const stats = ref<any>({})
const site = computed(() => stats.value.site || {})
const system = computed(() => stats.value.system || {})
const lastPublish = computed(() => stats.value.lastPublish || null)

const cards = computed(() => [
  { label: '画廊主图', value: site.value.figures ?? 0, hint: '静态站 figures.json', color: '#4f46e5', icon: 'ep:picture' },
  { label: '论文库', value: stats.value.papers ?? 0, hint: '待裁剪 ' + (stats.value.toCrop ?? 0), color: '#409eff', icon: 'ep:document' },
  { label: '待审核', value: stats.value.pendingReview ?? 0, hint: '已通过 ' + (stats.value.approved ?? 0), color: '#e6a23c', icon: 'ep:finished' },
  { label: '已发布', value: stats.value.published ?? 0, hint: '运行中任务 ' + (stats.value.runningJobs ?? 0), color: '#67c23a', icon: 'ep:upload' }
])

const venueRows = computed(() =>
  Object.entries(stats.value.byVenue || {}).map(([venue, count]) => ({ venue: String(venue).toUpperCase(), count }))
)

const statusText = (s: number) =>
  ({ 0: '等待', 1: '运行中', 2: '成功', 3: '失败', 4: '已取消' } as any)[s] || '-'
const statusType = (s: number) =>
  ({ 0: 'info', 1: 'warning', 2: 'success', 3: 'danger', 4: 'info' } as any)[s] || 'info'

const load = async () => {
  loading.value = true
  try {
    stats.value = await getDashboard()
  } finally {
    loading.value = false
  }
}
onMounted(load)
</script>
