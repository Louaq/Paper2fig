<template>
  <div>
    <ContentWrap>
      <el-form class="-mb-15px" :inline="true" label-width="68px">
        <el-form-item label="会议">
          <el-select v-model="query.venue" clearable placeholder="全部" class="!w-140px">
            <el-option v-for="v in venues" :key="v.optionValue" :label="optionLabel(v)" :value="v.optionValue" />
          </el-select>
        </el-form-item>
        <el-form-item label="年份">
          <el-select v-model="query.year" clearable placeholder="全部" class="!w-120px">
            <el-option v-for="y in years" :key="y" :label="y" :value="y" />
          </el-select>
        </el-form-item>
        <el-form-item label="等级">
          <el-select v-model="query.tier" clearable placeholder="全部" class="!w-140px">
            <el-option v-for="t in tiers" :key="t.optionValue" :label="optionLabel(t)" :value="t.optionValue" />
          </el-select>
        </el-form-item>
        <el-form-item label="模式">
          <el-select v-model="query.pattern" clearable placeholder="全部" class="!w-160px">
            <el-option v-for="p in patterns" :key="p.optionValue" :label="optionLabel(p)" :value="p.optionValue" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input
            v-model="query.keyword"
            placeholder="标题 / 作者 / 图片 ID"
            clearable
            class="!w-220px"
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery"><Icon icon="ep:search" class="mr-5px" />搜索</el-button>
          <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" />重置</el-button>
        </el-form-item>
      </el-form>
    </ContentWrap>

    <ContentWrap>
      <div class="mb-10px flex items-center justify-between">
        <span class="text-13px text-gray-500">
          共 {{ total.toLocaleString() }} 张图片 · 顺序与静态站一致（会议 → 年份 → 图片 ID） · 修改会立即写入静态站 figures.json / figures.js
        </span>
      </div>

      <el-table v-loading="loading" :data="list" size="small" row-key="id">
        <el-table-column label="预览" width="112">
          <template #default="{ row }">
            <el-image
              v-if="row.imageUrl"
              :src="row.imageUrl"
              :preview-src-list="[row.imageUrl]"
              preview-teleported
              fit="contain"
              style="width: 104px; height: 64px"
            />
            <span v-else class="text-gray-400">无图片</span>
          </template>
        </el-table-column>
        <el-table-column label="图片 ID" prop="figureId" min-width="150" show-overflow-tooltip />
        <el-table-column label="标题" prop="title" min-width="220" show-overflow-tooltip />
        <el-table-column label="会议" width="82">
          <template #default="{ row }">{{ venueName(row.venue) }}</template>
        </el-table-column>
        <el-table-column label="年份" prop="year" width="62" align="center" />
        <el-table-column label="来源" width="68" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.manualEditedTime" size="small" type="warning">人工</el-tag>
            <span v-else class="text-12px text-gray-400">自动</span>
          </template>
        </el-table-column>
        <el-table-column label="等级 / 奖项" width="108">
          <template #default="{ row }">
            <el-tag v-if="row.award === 'best'" size="small" type="warning">最佳论文</el-tag>
            <el-tag v-else-if="row.award === 'honorable'" size="small" type="warning">荣誉提名</el-tag>
            <el-tag v-else-if="row.tier" size="small" type="success">{{ tierName(row.tier) }}</el-tag>
            <span v-else class="text-gray-400">-</span>
          </template>
        </el-table-column>
        <el-table-column label="模式" width="100">
          <template #default="{ row }">{{ patternName(row.pattern) }}</template>
        </el-table-column>
        <el-table-column label="尺寸" width="90" align="center">
          <template #default="{ row }">{{ row.width || '-' }} × {{ row.height || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="108" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row.id)" v-hasPermi="['gallery:figure:update']">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row)" v-hasPermi="['gallery:figure:delete']">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="mt-16px justify-end"
        background
        :total="total"
        v-model:current-page="query.pageNo"
        v-model:page-size="query.pageSize"
        :page-sizes="[24, 48, 96]"
        layout="total, sizes, prev, pager, next, jumper"
        @change="load"
      />
    </ContentWrap>

    <el-dialog v-model="editVisible" title="编辑图片属性" width="760px" destroy-on-close>
      <div v-loading="editLoading">
        <el-form label-width="90px">
          <el-form-item label="图片预览">
            <div class="flex items-center gap-16px">
              <el-image
                v-if="previewUrl"
                :src="previewUrl"
                :preview-src-list="[previewUrl]"
                preview-teleported
                fit="contain"
                style="width: 240px; height: 140px; border: 1px solid #eee"
              />
              <el-upload
                :show-file-list="false"
                accept="image/*"
                :http-request="handleUpload"
                v-hasPermi="['gallery:figure:update']"
              >
                <el-button :loading="uploading">替换图片</el-button>
                <template #tip>
                  <div class="el-upload__tip">支持 JPG/PNG，至少 200×100，上传后统一转为 JPEG</div>
                </template>
              </el-upload>
            </div>
          </el-form-item>
          <el-form-item label="图片 ID">
            <el-input :model-value="edit.figureId" disabled />
          </el-form-item>
          <el-form-item label="标题" required>
            <el-input v-model="edit.title" maxlength="500" show-word-limit />
          </el-form-item>
          <el-form-item label="作者">
            <el-input
              v-model="authorsText"
              type="textarea"
              :rows="3"
              placeholder="每行一位作者"
            />
          </el-form-item>
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="会议" required>
                <el-select v-model="edit.venue" class="w-full">
                  <el-option v-for="v in venues" :key="v.optionValue" :label="optionLabel(v)" :value="v.optionValue" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="年份" required>
                <el-input-number v-model="edit.year" :min="1900" :max="2100" controls-position="right" class="w-full" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="模式">
                <el-select v-model="edit.pattern" clearable class="w-full">
                  <el-option v-for="p in patterns" :key="p.optionValue" :label="optionLabel(p)" :value="p.optionValue" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="等级">
                <el-select v-model="edit.tier" clearable class="w-full">
                  <el-option v-for="t in editTiers" :key="t.optionValue" :label="optionLabel(t)" :value="t.optionValue" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="奖项">
                <el-select v-model="edit.award" clearable class="w-full">
                  <el-option label="最佳论文" value="best" />
                  <el-option label="荣誉提名" value="honorable" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="评分">
                <el-input-number v-model="edit.score" :precision="2" :step="1" controls-position="right" class="w-full" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="论文链接">
            <el-input v-model="edit.paper" />
          </el-form-item>
          <el-form-item label="PDF 链接">
            <el-input v-model="edit.pdfSource" />
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="edit.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  deleteFigure,
  getFigure,
  getFigurePage,
  getSiteOptions,
  updateFigure,
  uploadFigureImage
} from '@/api/gallery'
import { useRefreshOnActivate } from '@/hooks/web/useRefreshOnActivate'

defineOptions({ name: 'GalleryFigure' })

const loading = ref(false)
const list = ref<any[]>([])
const total = ref(0)
const query = reactive<any>({ pageNo: 1, pageSize: 24, venue: '', year: null, tier: '', pattern: '', keyword: '' })

const options = ref<any[]>([])
const venues = computed(() => options.value.filter((o) => o.dimension === 'venue'))
const years = computed(() => options.value.filter((o) => o.dimension === 'year').map((o) => Number(o.optionValue)))
const tiers = computed(() => options.value.filter((o) => o.dimension === 'tier'))
const editTiers = computed(() => tiers.value.filter((o) => o.optionValue !== 'best'))
const patterns = computed(() => options.value.filter((o) => o.dimension === 'pattern'))

const editVisible = ref(false)
const editLoading = ref(false)
const uploading = ref(false)
const saving = ref(false)
const edit = reactive<any>({})
const authorsText = ref('')
const previewUrl = ref('')

const optionLabel = (o: any) => o.labelZh || o.labelEn || o.optionValue
const venueName = (value: string) => {
  const match = venues.value.find((o) => o.optionValue === value)
  return match ? optionLabel(match) : value
}
const tierName = (value: string) => {
  const match = tiers.value.find((o) => o.optionValue === value)
  return match ? optionLabel(match) : value
}
const patternName = (value: string) => {
  const match = patterns.value.find((o) => o.optionValue === value)
  return match ? optionLabel(match) : value || '-'
}

const loadOptions = async () => {
  options.value = (await getSiteOptions()) || []
}
const load = async () => {
  loading.value = true
  try {
    const data = await getFigurePage({ ...query })
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
const handleQuery = () => {
  query.pageNo = 1
  load()
}
const resetQuery = () => {
  Object.assign(query, { pageNo: 1, venue: '', year: null, tier: '', pattern: '', keyword: '' })
  load()
}

const openEdit = async (id: number) => {
  editVisible.value = true
  editLoading.value = true
  try {
    const data = await getFigure(id)
    Object.assign(edit, data)
    authorsText.value = (data.authors || []).join('\n')
    previewUrl.value = data.imageUrl ? data.imageUrl + '?t=' + Date.now() : ''
  } finally {
    editLoading.value = false
  }
}

const saveEdit = async () => {
  if (!edit.title?.trim()) return ElMessage.warning('请填写标题')
  saving.value = true
  try {
    await updateFigure({
      id: edit.id,
      title: edit.title,
      authors: authorsText.value.split(/\n+/).map((s) => s.trim()).filter(Boolean),
      venue: edit.venue,
      year: edit.year,
      pattern: edit.pattern || null,
      tier: edit.tier || null,
      award: edit.award || null,
      paper: edit.paper || null,
      pdfSource: edit.pdfSource || null,
      score: edit.score ?? null,
      remark: edit.remark || null
    })
    ElMessage.success('已保存，静态站已更新')
    editVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

const handleUpload = async (options: any) => {
  uploading.value = true
  try {
    await uploadFigureImage(edit.id, options.file)
    ElMessage.success('图片已替换')
    previewUrl.value = '/' + edit.image + '?t=' + Date.now()
    edit.imageUrl = previewUrl.value
    await load()
  } finally {
    uploading.value = false
  }
}

const handleDelete = async (row: any) => {
  await ElMessageBox.confirm(
    `确定删除《${row.title}》？将同时删除静态站图片文件。`,
    '删除确认',
    { type: 'warning' }
  )
  await deleteFigure(row.id)
  ElMessage.success('已删除')
  await load()
}

const refresh = async () => {
  try {
    await loadOptions()
  } catch {
    // 筛选项加载失败（如无前端配置权限）不应阻塞图片列表
  }
  await load()
}
useRefreshOnActivate(refresh)
</script>
