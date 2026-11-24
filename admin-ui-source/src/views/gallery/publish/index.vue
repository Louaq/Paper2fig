<template>
  <div>
    <el-row :gutter="16">
      <el-col :span="6">
        <el-card shadow="hover"><div class="text-12px text-gray-500">已审核通过</div><div class="text-26px font-bold">{{ summary.ready ?? 0 }}</div></el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="text-12px text-gray-500">人工修改冲突</div>
          <div class="text-26px font-bold" :class="(summary.conflicts || []).length ? 'text-orange-500' : ''">{{ (summary.conflicts || []).length }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover"><div class="text-12px text-gray-500">存在问题的记录</div><div class="text-26px font-bold text-red-500">{{ (summary.missing || []).length }}</div></el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="text-12px text-gray-500 mb-4px">站点目录</div>
          <div class="text-12px break-all">{{ summary.siteDir }}</div>
        </el-card>
      </el-col>
    </el-row>

    <ContentWrap title="发布操作" class="mt-16px">
      <template #header>
        <div class="flex w-full justify-end">
          <el-tooltip content="刷新概览和发布记录" placement="top">
            <el-button link @click="loadAll">
              <Icon icon="ep:refresh" class="mr-4px" />刷新
            </el-button>
          </el-tooltip>
        </div>
      </template>

      <div class="publish-layout">
        <div class="publish-layout__info">
          <div class="publish-layout__title">发布到静态站</div>
          <div class="publish-layout__desc">
            将审核通过的记录合并进画廊目录，并重新渲染
            <code>figures.json</code>、<code>figures.js</code> 和 <code>sitemap.xml</code>，
            同时把目录同步到 Meilisearch 检索索引（失败只记日志，不影响发布）。
            发布成功后会自动删除本批论文的原始 PDF 与工作区裁剪图；失败会从备份回滚。
          </div>
          <div class="publish-layout__option">
            <el-checkbox v-model="forceOverwrite">覆盖人工修改</el-checkbox>
            <span class="publish-layout__hint">默认跳过在「图片管理」中人工编辑过的记录</span>
          </div>
        </div>

        <div class="publish-layout__actions">
          <div class="publish-layout__stats">
            <span>待发布 <b class="text-[#409eff]">{{ summary.ready ?? 0 }}</b> 条</span>
            <span>
              人工冲突
              <b :class="(summary.conflicts || []).length ? 'text-[#e6a23c]' : 'text-gray-500'">
                {{ (summary.conflicts || []).length }}
              </b>
              条
            </span>
          </div>
          <div class="publish-layout__buttons">
            <el-tooltip content="把当前已发布目录全量重新写入 Meilisearch 的 figures 索引；不修改静态站文件。" placement="top">
              <el-button :loading="syncing" @click="doSyncSearch" v-hasPermi="['gallery:publish:execute']">
                <Icon icon="ep:search" class="mr-5px" />重建搜索索引
              </el-button>
            </el-tooltip>
            <el-tooltip content="删除已发布论文的原始 PDF 和工作区裁剪图；不影响静态站已发布图片。" placement="top">
              <el-button type="warning" plain :loading="cleanupLoading" @click="doCleanupArtifacts" v-hasPermi="['gallery:publish:execute']">
                <Icon icon="ep:delete" class="mr-5px" />清理已发布原始文件
              </el-button>
            </el-tooltip>
            <el-tooltip :disabled="canPublish" content="当前没有可发布的记录" placement="top">
              <span>
                <el-button type="primary" :loading="publishing" :disabled="!canPublish"
                  @click="doPublish" v-hasPermi="['gallery:publish:execute']">
                  <Icon icon="ep:upload" class="mr-5px" />执行发布
                </el-button>
              </span>
            </el-tooltip>
          </div>
          <div class="publish-layout__hint text-right">清理只影响工作区原始文件，不会删除静态站图片</div>
        </div>
      </div>
    </ContentWrap>

    <ContentWrap title="发布记录">
      <el-table v-loading="loading" :data="records" size="small">
        <el-table-column label="版本" prop="version" width="150" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 1 ? 'success' : row.status === 2 ? 'danger' : 'info'">
              {{ row.status === 1 ? '成功' : row.status === 2 ? '失败' : '预检' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="新增" prop="added" width="80" />
        <el-table-column label="更新" prop="updated" width="80" />
        <el-table-column label="总数" prop="total" width="90" />
        <el-table-column label="操作人" prop="operatorName" width="110" />
        <el-table-column label="时间" prop="createTime" width="170" />
        <el-table-column label="操作" width="130">
          <template #default="{ row }">
            <el-button link type="primary" @click="showResult(row)">查看</el-button>
            <el-button link type="danger" @click="deleteRecord(row)" v-hasPermi="['gallery:publish:execute']">删除</el-button>
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
        @change="loadRecords"
      />
    </ContentWrap>

    <el-dialog v-model="resultVisible" title="结果" width="720px">
      <pre class="result-box">{{ resultText }}</pre>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { cleanupPublishedArtifacts, deletePublishRecord, executePublish, getPublishPage, getPublishSummary, syncSearchIndex } from '@/api/gallery'
import { useRefreshOnActivate } from '@/hooks/web/useRefreshOnActivate'

defineOptions({ name: 'GalleryPublish' })

const loading = ref(false)
const publishing = ref(false)
const summary = ref<any>({})
const records = ref<any[]>([])
const total = ref(0)
const query = reactive<any>({ pageNo: 1, pageSize: 10 })
const resultVisible = ref(false)
const resultText = ref('')
const forceOverwrite = ref(false)
const cleanupLoading = ref(false)
const syncing = ref(false)
const canPublish = computed(() => Boolean(summary.value.ready) || (summary.value.conflicts || []).length > 0)

const loadSummary = async () => { summary.value = await getPublishSummary() }
const loadRecords = async () => {
  loading.value = true
  try {
    const data = await getPublishPage({ ...query })
    records.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
const loadAll = async () => { await Promise.all([loadSummary(), loadRecords()]) }

const doPublish = async () => {
  await ElMessageBox.confirm(
    `将发布 ${summary.value.ready} 条记录到 ${summary.value.siteDir}` +
      ((summary.value.conflicts || []).length
        ? `；有 ${summary.value.conflicts.length} 条人工修改冲突${forceOverwrite.value ? '（将强制覆盖）' : '（默认跳过）'}`
        : '') +
      '，是否继续？',
    '确认发布', { type: 'warning' }
  )
  publishing.value = true
  try {
    const data = await executePublish(forceOverwrite.value)
    resultText.value = JSON.stringify(data, null, 2)
    resultVisible.value = true
    ElMessage[data.ok ? 'success' : 'error'](data.ok ? '发布成功' : '发布失败，已保留旧画廊')
    await loadAll()
  } finally {
    publishing.value = false
  }
}
const doSyncSearch = async () => {
  await ElMessageBox.confirm(
    '将把当前已发布目录全量重新写入 Meilisearch 的 figures 索引，是否继续？',
    '重建搜索索引', { type: 'warning' }
  )
  syncing.value = true
  try {
    const data: any = await syncSearchIndex()
    resultText.value = JSON.stringify(data, null, 2)
    resultVisible.value = true
    ElMessage[data.ok ? 'success' : 'warning'](
      data.ok ? `索引重建完成，共 ${data.indexed ?? '-'} 条，耗时 ${data.durationMs ?? '-'}ms` : `索引重建未成功：${data.error || '见结果详情'}`
    )
  } finally {
    syncing.value = false
  }
}
const doCleanupArtifacts = async () => {
  await ElMessageBox.confirm(
    '将删除所有已发布论文的原始 PDF、工作区裁剪图和已发布论文对应的裁剪任务日志，是否继续？',
    '清理原始文件', { type: 'warning' }
  )
  cleanupLoading.value = true
  try {
    const data: any = await cleanupPublishedArtifacts(false)
    resultText.value = JSON.stringify(data, null, 2)
    resultVisible.value = true
    ElMessage.success(`清理完成，释放 ${data.freed || '0 B'}`)
    await loadAll()
  } finally {
    cleanupLoading.value = false
  }
}
const showResult = (row: any) => {
  resultText.value = row.resultJson || '（无记录）'
  resultVisible.value = true
}
const deleteRecord = async (row: any) => {
  await ElMessageBox.confirm(`确定删除发布记录「${row.version}」？只删除该条记录，不影响已发布静态站。`, '删除确认', { type: 'warning' })
  await deletePublishRecord(row.id)
  ElMessage.success('已删除')
  await loadRecords()
}

useRefreshOnActivate(loadAll)
</script>

<style scoped>
.publish-layout {
  display: flex;
  flex-wrap: wrap;
  gap: 24px;
}
.publish-layout__info {
  flex: 1 1 420px;
  min-width: 0;
  padding-right: 24px;
  border-right: 1px solid #f0f0f0;
}
.publish-layout__title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}
.publish-layout__desc {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.8;
  color: #909399;
}
.publish-layout__desc code {
  padding: 1px 4px;
  border-radius: 3px;
  background: #f5f7fa;
  color: #606266;
}
.publish-layout__option {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-top: 14px;
}
.publish-layout__hint {
  font-size: 12px;
  color: #a8abb2;
}
.publish-layout__actions {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  justify-content: center;
  gap: 12px;
  flex: 0 0 auto;
}
.publish-layout__stats {
  display: flex;
  gap: 20px;
  font-size: 13px;
  color: #606266;
}
.publish-layout__buttons {
  display: flex;
  align-items: center;
  gap: 12px;
}
.publish-layout__buttons :deep(.el-button) {
  min-width: 172px;
  margin-left: 0;
}
@media (max-width: 900px) {
  .publish-layout__info {
    flex-basis: 100%;
    padding-right: 0;
    border-right: 0;
    border-bottom: 1px solid #f0f0f0;
    padding-bottom: 16px;
  }
  .publish-layout__actions {
    width: 100%;
    align-items: stretch;
  }
  .publish-layout__stats {
    justify-content: space-between;
  }
  .publish-layout__buttons {
    flex-direction: column-reverse;
    align-items: stretch;
  }
  .publish-layout__buttons :deep(.el-button) {
    width: 100%;
  }
  .publish-layout__hint.text-right {
    text-align: left;
  }
}
.result-box {
  max-height: 55vh;
  overflow: auto;
  background: #f7f8fa;
  padding: 12px;
  border-radius: 6px;
  font-size: 12px;
  line-height: 1.6;
}
</style>
