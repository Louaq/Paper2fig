<template>
  <ContentWrap>
    <el-form :inline="true" :model="query" class="mb-10px">
      <el-form-item label="会议">
        <el-select v-model="query.venue" clearable placeholder="全部" class="!w-130px">
          <el-option v-for="v in venues" :key="v.value" :label="v.label" :value="v.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="年份">
        <el-input v-model.number="query.year" placeholder="如 2025" clearable class="!w-110px" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" clearable placeholder="全部" class="!w-140px">
          <el-option v-for="s in paperStatus" :key="s.value" :label="s.label" :value="s.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-checkbox v-model="query.includePublished" @change="handleQuery">显示已发布</el-checkbox>
      </el-form-item>
      <el-form-item label="关键词">
        <el-input v-model="query.keyword" placeholder="标题 / 作者 / DOI" clearable class="!w-220px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item>
        <div class="toolbar-buttons">
          <el-button type="primary" @click="handleQuery"><Icon icon="ep:search" class="mr-5px" />搜索</el-button>
          <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" />重置</el-button>
          <el-button type="warning" plain @click="openUpload" v-hasPermi="['gallery:job:create']">
            <Icon icon="ep:upload" class="mr-5px" />上传 PDF
          </el-button>
          <el-button type="primary" plain @click="openCrawl" v-hasPermi="['gallery:job:create']">
            <Icon icon="ep:download" class="mr-5px" />采集论文
          </el-button>
          <el-button type="success" plain @click="openCrop" v-hasPermi="['gallery:job:create']">
            <Icon icon="ep:scissor" class="mr-5px" />批量裁剪
          </el-button>
          <el-button
            type="danger"
            plain
            :loading="clearingLibrary"
            @click="clearLibrary"
            v-hasPermi="['gallery:paper:delete']"
          >
            <Icon icon="ep:delete" class="mr-5px" />清空论文库
          </el-button>
        </div>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="list" size="small" @selection-change="onSelectionChange">
      <el-table-column type="selection" width="45" />
      <el-table-column label="预览" width="120">
        <template #default="{ row }">
          <el-image v-if="row.thumb" :src="row.thumb" fit="contain" style="width: 104px; height: 64px" />
          <span v-else class="text-gray-400">未裁剪</span>
        </template>
      </el-table-column>
      <el-table-column label="标题 / 作者" min-width="330">
        <template #default="{ row }">
          <div class="font-medium">{{ row.title }}</div>
          <div class="text-12px text-gray-500">{{ (row.authors || []).slice(0, 4).join('; ') }}</div>
        </template>
      </el-table-column>
      <el-table-column label="会议" width="110">
        <template #default="{ row }">{{ row.venue.toUpperCase() }} {{ row.year }}</template>
      </el-table-column>
      <el-table-column label="来源" prop="source" width="90" />
      <el-table-column label="状态" width="130">
        <template #default="{ row }">
          <el-tag size="small" :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          <el-tag v-if="row.pdfStatus === 1" size="small" type="info" class="ml-4px">PDF 已缓存</el-tag>
          <el-tag v-if="row.pdfStatus === 2" size="small" type="danger" class="ml-4px">PDF失败</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="置信度" width="90">
        <template #default="{ row }">{{ row.confidence ? (row.confidence * 100).toFixed(0) + '%' : '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="130" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row.id)">详情</el-button>
          <el-button link type="danger" @click="handleDelete(row)" v-hasPermi="['gallery:paper:delete']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      class="mt-16px justify-end"
      background
      :total="total"
      v-model:current-page="query.pageNo"
      v-model:page-size="query.pageSize"
      :page-sizes="[10, 20, 50, 100]"
      layout="total, sizes, prev, pager, next"
      @change="load"
    />
  </ContentWrap>

  <el-dialog v-model="cropVisible" title="批量裁剪 + 质检" width="480px">
    <el-form label-width="90px">
      <el-form-item label="裁剪范围">
        <span>{{ selectedRows.length ? `已选中 ${selectedRows.length} 篇论文` : `当前页 ${list.length} 篇论文` }}</span>
        <div class="text-12px text-gray-500 ml-8px">默认自动下载缺失的 PDF；若仅使用本地缓存可关闭下方开关</div>
      </el-form-item>
      <el-form-item label="下载 PDF">
        <el-switch v-model="cropForm.autoFetch" />
        <div class="text-12px text-gray-500 ml-8px">
          {{ cropForm.autoFetch ? '缺失时自动抓取（AAAI 需通过人机校验，可能稍慢）' : '不抓取，只使用已缓存的 PDF' }}
        </div>
      </el-form-item>
      <el-form-item label="DPI">
        <el-input-number v-model="cropForm.dpi" :min="120" :max="400" />
      </el-form-item>
      <el-form-item label="包含图注">
        <el-switch v-model="cropForm.includeCaption" />
      </el-form-item>
      <el-form-item label="解析方式">
        <el-radio-group v-model="cropForm.parseProvider">
          <el-radio value="local">本地环境</el-radio>
          <el-radio value="mineru">MinerU 在线 API</el-radio>
        </el-radio-group>
        <div v-if="cropForm.parseProvider === 'mineru'" class="text-12px text-gray-500 ml-8px">
          需在「管线设置」配置 MinerU API Token
        </div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="cropVisible = false">取消</el-button>
      <el-button type="primary" :loading="creatingCrop" @click="submitCrop">创建任务</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="crawlVisible" title="采集论文" width="440px">
    <el-form label-width="80px">
      <el-form-item label="会议">
        <el-select v-model="crawlForm.venue" class="!w-full">
          <el-option v-for="v in venues" :key="v.value" :label="v.label" :value="v.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="年份">
        <el-input-number v-model="crawlForm.year" :min="2020" :max="2030" />
      </el-form-item>
      <el-form-item label="上限">
        <el-input-number v-model="crawlForm.limit" :min="0" :max="5000" />
        <div class="text-12px text-gray-500 ml-8px">0 表示抓取全部</div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="crawlVisible = false">取消</el-button>
      <el-button type="primary" :loading="creatingCrawl" @click="submitCrawl">创建任务</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="uploadVisible" title="手动上传论文 PDF" width="560px">
    <el-form label-width="80px">
      <el-form-item label="PDF 文件">
        <el-upload
          ref="uploadRef"
          drag
          :auto-upload="false"
          :limit="1"
          accept=".pdf,application/pdf"
          :on-change="onPdfChange"
          :on-exceed="onPdfExceed"
          class="w-full"
        >
          <el-icon class="el-icon--upload"><upload-filled /></el-icon>
          <div class="el-upload__text">拖拽 PDF 到此处，或<em>点击选择</em></div>
          <template #tip>
            <div class="el-upload__tip">仅支持 PDF，单个文件不超过 100MB；上传后即可在列表中「批量裁剪」</div>
          </template>
        </el-upload>
      </el-form-item>
      <el-form-item label="会议" required>
        <el-select v-model="uploadForm.venue" placeholder="请选择会议" class="!w-full">
          <el-option v-for="v in venues" :key="v.value" :label="v.label" :value="v.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="年份" required>
        <el-input-number v-model="uploadForm.year" :min="2000" :max="2100" />
      </el-form-item>
      <el-form-item label="标题">
        <el-input v-model="uploadForm.title" placeholder="留空时使用文件名" maxlength="500" />
      </el-form-item>
      <el-form-item label="作者">
        <el-input v-model="uploadForm.authors" placeholder="多个作者用分号或逗号分隔（可选）" />
      </el-form-item>
      <el-form-item label="DOI">
        <el-input v-model="uploadForm.doi" placeholder="可选，例如 10.1145/xxxx" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="uploadVisible = false">取消</el-button>
      <el-button type="primary" :loading="uploading" @click="submitUpload">上传</el-button>
    </template>
  </el-dialog>

  <el-drawer v-model="detailVisible" title="论文详情" size="820px">
    <div v-if="detail" v-loading="detailLoading">
      <el-image v-if="detail.thumb" :src="detail.thumb" fit="contain" class="w-full mb-12px" />
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="标题" :span="2">{{ detail.title }}</el-descriptions-item>
        <el-descriptions-item label="会议">{{ detail.venue.toUpperCase() }} {{ detail.year }}</el-descriptions-item>
        <el-descriptions-item label="来源">{{ detail.source }} / {{ detail.sourceId }}</el-descriptions-item>
        <el-descriptions-item label="作者" :span="2">{{ (detail.authors || []).join('; ') }}</el-descriptions-item>
        <el-descriptions-item label="原文" :span="2">
          <el-link :href="detail.paperUrl" target="_blank" type="primary">{{ detail.paperUrl }}</el-link>
        </el-descriptions-item>
        <el-descriptions-item label="PDF 页数">{{ detail.pdfPages || '-' }}</el-descriptions-item>
        <el-descriptions-item label="裁剪尺寸">
          {{ detail.cropWidth || '-' }} × {{ detail.cropHeight || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="置信度">{{ detail.confidence?.toFixed(2) || '-' }}</el-descriptions-item>
        <el-descriptions-item label="标记">{{ detail.flags || '[]' }}</el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">标注</el-divider>
      <el-form label-width="90px" class="mt-10px">
        <el-form-item label="视觉模式">
          <el-select v-model="edit.pattern" class="!w-200px">
            <el-option v-for="p in patterns" :key="p" :label="p" :value="p" />
          </el-select>
        </el-form-item>
        <el-form-item label="等级">
          <el-select v-model="edit.tier" clearable class="!w-200px">
            <el-option label="Oral" value="oral" />
            <el-option label="Spotlight" value="spotlight" />
          </el-select>
        </el-form-item>
        <el-form-item label="奖项">
          <el-select v-model="edit.award" clearable class="!w-200px">
            <el-option label="Best Paper" value="best" />
            <el-option label="Honorable" value="honorable" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="saveEdit" v-hasPermi="['gallery:paper:update']">保存</el-button>
        </el-form-item>
      </el-form>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, nextTick, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createCrawlJob, createCropJob, clearAllPapers, deletePaper, getPaper, getPaperPage, updatePaper, uploadPaperPdf } from '@/api/gallery'
import { useRefreshOnActivate } from '@/hooks/web/useRefreshOnActivate'

defineOptions({ name: 'GalleryPaper' })

const venues = [
  { value: 'iclr', label: 'ICLR' }, { value: 'icml', label: 'ICML' }, { value: 'neurips', label: 'NeurIPS' },
  { value: 'cvpr', label: 'CVPR' }, { value: 'acl', label: 'ACL' }, { value: 'aaai', label: 'AAAI' },
  { value: 'eccv', label: 'ECCV' }, { value: 'miccai', label: 'MICCAI' },
  { value: 'ijcai', label: 'IJCAI' }, { value: 'icme', label: 'ICME' },
  { value: 'mm', label: 'ACM MM' }
]
const paperStatus = [
  { value: 0, label: '待处理' }, { value: 1, label: '已裁剪待审' }, { value: 2, label: '审核通过' },
  { value: 3, label: '已拒绝' }, { value: 4, label: '已发布' }
]
const patterns = ['teaser', 'conceptual', 'framework', 'pipeline', 'architecture', 'taxonomy', 'results', 'comparison']

const loading = ref(false)
const list = ref<any[]>([])
const total = ref(0)
const query = reactive<any>({ pageNo: 1, pageSize: 10, venue: '', year: null, status: null, keyword: '', includePublished: false })

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<any>(null)
const edit = reactive<any>({ id: null, pattern: '', tier: '', award: '' })

const statusText = (s: number) =>
  ({ 0: '待处理', 1: '已裁剪待审', 2: '审核通过', 3: '已拒绝', 4: '已发布' } as any)[s] || '-'
const statusType = (s: number) =>
  ({ 0: 'info', 1: 'warning', 2: '', 3: 'danger', 4: 'success' } as any)[s] || 'info'


const load = async () => {
  loading.value = true
  try {
    const data = await getPaperPage({ ...query })
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

const handleQuery = () => { query.pageNo = 1; load() }
const resetQuery = () => {
  Object.assign(query, { pageNo: 1, venue: '', year: null, status: null, keyword: '', includePublished: false })
  load()
}
const openDetail = async (id: number) => {
  detailVisible.value = true
  detailLoading.value = true
  try {
    detail.value = await getPaper(id)
    Object.assign(edit, {
      id, pattern: detail.value.pattern || 'teaser',
      tier: detail.value.tier || '', award: detail.value.award || ''
    })
  } finally {
    detailLoading.value = false
  }
}

const saveEdit = async () => {
  await updatePaper({ ...edit })
  ElMessage.success('已保存')
  await load()
}

const handleDelete = async (row: any) => {
  await ElMessageBox.confirm(`确定删除《${row.title}》？`, '提示', { type: 'warning' })
  await deletePaper(row.id)
  ElMessage.success('已删除')
  await load()
}

// ---------------- 批量选择 / 清空论文库 ----------------
const selectedRows = ref<any[]>([])
const selectedIds = computed(() => selectedRows.value.map((row) => row.id))
const onSelectionChange = (rows: any[]) => {
  selectedRows.value = rows
}
const clearingLibrary = ref(false)
const clearLibrary = async () => {
  try {
    await ElMessageBox.confirm(
      '确定清空论文库？将彻底删除全部论文记录、PDF 文件和已裁剪的图片（审核队列也会清空），此操作不可恢复。已发布到静态站的图片不受影响。',
      '清空论文库',
      { type: 'warning', confirmButtonClass: 'el-button--danger' }
    )
  } catch {
    return
  }
  clearingLibrary.value = true
  try {
    const result: any = await clearAllPapers()
    ElMessage.success(
      `已清空论文库：论文 ${result.papers ?? 0} 篇、裁剪记录 ${result.crops ?? 0} 条、文件 ${result.files ?? 0} 个`
    )
    selectedRows.value = []
    await load()
  } finally {
    clearingLibrary.value = false
  }
}

const cropVisible = ref(false)
const creatingCrop = ref(false)
const cropForm = reactive<any>({ dpi: 200, includeCaption: false, parseProvider: 'local', autoFetch: true })
// 有勾选时只裁剪勾选的论文，否则裁剪当前页
const cropRows = computed(() => (selectedRows.value.length ? selectedRows.value : list.value))
const openCrop = () => {
  if (!cropRows.value.length) return ElMessage.warning('当前页没有论文')
  cropVisible.value = true
}
const submitCrop = async () => {
  const rows = cropRows.value
  if (!cropForm.autoFetch && rows.length
      && rows.every((row) => row.pdfStatus !== 1)) {
    try {
      await ElMessageBox.confirm(
        '所选论文都还没有缓存的 PDF，且已关闭「下载 PDF」，任务会全部失败。仍要创建吗？',
        '提示',
        { type: 'warning' }
      )
    } catch {
      return
    }
  }
  creatingCrop.value = true
  try {
    const { autoFetch, ...rest } = cropForm
    await createCropJob({
      paperIds: rows.map((row) => row.id),
      noFetch: !autoFetch,
      ...rest
    })
    ElMessage.success('裁剪任务已创建，可在“论文日志”查看')
    cropVisible.value = false
  } finally {
    creatingCrop.value = false
  }
}

const crawlVisible = ref(false)
const creatingCrawl = ref(false)
const crawlForm = reactive<any>({
  venue: query.venue || 'cvpr',
  year: query.year || new Date().getFullYear(),
  limit: 20
})
const openCrawl = () => {
  crawlForm.venue = query.venue || 'cvpr'
  crawlForm.year = query.year || new Date().getFullYear()
  crawlVisible.value = true
}
const submitCrawl = async () => {
  creatingCrawl.value = true
  try {
    await createCrawlJob({ ...crawlForm })
    ElMessage.success('采集任务已创建，可在“论文日志”查看进度')
    crawlVisible.value = false
  } finally {
    creatingCrawl.value = false
  }
}

// ---------------- 手动上传 PDF ----------------
const uploadRef = ref<any>(null)
const uploadVisible = ref(false)
const uploading = ref(false)
const uploadForm = reactive<any>({
  file: null as File | null,
  venue: '',
  year: new Date().getFullYear(),
  title: '',
  authors: '',
  doi: ''
})
const openUpload = () => {
  uploadForm.file = null
  uploadForm.venue = query.venue || 'cvpr'
  uploadForm.year = query.year || new Date().getFullYear()
  uploadForm.title = ''
  uploadForm.authors = ''
  uploadForm.doi = ''
  uploadVisible.value = true
  nextTick(() => uploadRef.value?.clearFiles())
}
const onPdfChange = (file: any) => {
  uploadForm.file = file.raw || null
  if (!uploadForm.title && file.name) {
    uploadForm.title = String(file.name).replace(/\.pdf$/i, '')
  }
}
const onPdfExceed = (files: File[]) => {
  uploadRef.value?.clearFiles()
  uploadRef.value?.handleStart(files[0])
}
const submitUpload = async () => {
  if (!uploadForm.file) return ElMessage.warning('请选择 PDF 文件')
  if (!uploadForm.venue) return ElMessage.warning('请选择会议')
  if (!uploadForm.year) return ElMessage.warning('请填写年份')
  uploading.value = true
  try {
    const data = new FormData()
    data.append('file', uploadForm.file)
    data.append('venue', uploadForm.venue)
    data.append('year', String(uploadForm.year))
    if (uploadForm.title) data.append('title', uploadForm.title)
    if (uploadForm.authors) data.append('authors', uploadForm.authors)
    if (uploadForm.doi) data.append('doi', uploadForm.doi)
    await uploadPaperPdf(data)
    ElMessage.success('PDF 已上传，勾选后可「批量裁剪」')
    uploadVisible.value = false
    await load()
  } finally {
    uploading.value = false
  }
}
useRefreshOnActivate(load)
</script>

<style scoped>
.toolbar-buttons {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.toolbar-buttons :deep(.el-button + .el-button) {
  margin-left: 0;
}
</style>
