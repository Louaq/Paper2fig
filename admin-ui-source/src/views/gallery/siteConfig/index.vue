<template>
  <div>
    <ContentWrap>
      <el-alert type="info" :closable="false" class="mb-12px">
        这里配置静态站首页的筛选按钮（会议 / 年份 / 等级 / 模式）。保存后会自动生成
        <code>assets/site-config.js</code> 与 <code>data/site-config.json</code>，刷新页面即可生效。<br />
        删除某个选项只会移除筛选按钮，不会删除该分类下的图片；如需彻底删除图片，请到「图片管理」操作。
      </el-alert>

      <el-tabs v-model="active">
        <el-tab-pane v-for="tab in tabs" :key="tab.key" :label="tab.label" :name="tab.key">
          <div class="mb-10px flex items-center justify-between">
            <span class="text-13px text-gray-500">{{ tab.tip }}</span>
            <el-button
              type="primary"
              plain
              v-hasPermi="['gallery:site-config:update']"
              @click="openCreate(tab.key)"
            >
              <Icon icon="ep:plus" class="mr-5px" />新增{{ tab.label }}
            </el-button>
          </div>

          <div v-loading="loading" class="venue-sort">
            <VueDraggable
              v-model="lists[tab.key]"
              item-key="id"
              handle=".drag-handle"
              :animation="150"
              @end="saveOrder(tab.key)"
            >
              <template #item="{ element: row }">
                <div :key="row.id" class="venue-sort__row">
                  <Icon icon="ep:rank" class="drag-handle" />
                  <div class="venue-sort__main">
                    <span class="venue-sort__value">{{ row.optionValue }}</span>
                    <span v-if="tab.key !== 'year'" class="venue-sort__labels">{{ row.labelZh }} / {{ row.labelEn }}</span>
                  </div>
                  <el-button link type="primary" v-hasPermi="['gallery:site-config:update']" @click="openEdit(row)">
                    编辑
                  </el-button>
                  <el-button link type="danger" v-hasPermi="['gallery:site-config:update']" @click="handleDelete(row)">
                    删除
                  </el-button>
                </div>
              </template>
            </VueDraggable>
            <el-empty v-if="!lists[tab.key].length && !loading" :description="`还没有${tab.label}，点击右上角新增`" :image-size="60" />
          </div>
        </el-tab-pane>
      </el-tabs>
    </ContentWrap>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑选项' : '新增选项'" width="520px">
      <el-form label-width="90px">
        <el-form-item label="维度">
          <el-input :model-value="dimensionName(form.dimension)" disabled />
        </el-form-item>
        <el-form-item :label="form.dimension === 'year' ? '年份' : '选项值'" required>
          <el-input v-model="form.optionValue" :placeholder="valuePlaceholder(form.dimension)" />
          <div v-if="form.id" class="text-12px text-orange-500 mt-4px">
            修改“选项值”不会自动同步已有图片的属性，请谨慎修改。
          </div>
        </el-form-item>
        <template v-if="form.dimension !== 'year'">
          <el-form-item label="中文名">
            <el-input v-model="form.labelZh" placeholder="留空则使用选项值" />
          </el-form-item>
          <el-form-item label="英文名">
            <el-input v-model="form.labelEn" placeholder="留空则使用选项值" />
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { nextTick, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import VueDraggable from 'vuedraggable'
import {
  createSiteOption,
  deleteSiteOption,
  getSiteOptions,
  sortSiteOptions,
  updateSiteOption
} from '@/api/gallery'
import { useRefreshOnActivate } from '@/hooks/web/useRefreshOnActivate'

defineOptions({ name: 'GallerySiteConfig' })

const tabs = [
  { key: 'venue', label: '会议', tip: '首页「会议」筛选按钮，按住左侧手柄拖动即可排序；新增会议后可在图片管理里选择' },
  { key: 'year', label: '年份', tip: '首页「年份」筛选按钮，按住左侧手柄拖动即可排序' },
  { key: 'tier', label: '等级', tip: '首页「等级」筛选按钮；按住左侧手柄拖动排序，best 代表最佳论文（对应奖项）' },
  { key: 'pattern', label: '模式', tip: '首页「模式」筛选按钮，例如主视觉 / 架构图 / 流程图；按住左侧手柄拖动排序' }
]

const active = ref('venue')
const loading = ref(false)
const saving = ref(false)
const lists = reactive<Record<string, any[]>>({ venue: [], year: [], tier: [], pattern: [] })
const dialogVisible = ref(false)
const form = reactive<any>({ id: null, dimension: 'venue', optionValue: '', labelZh: '', labelEn: '', sort: 0 })

const dimensionName = (dimension: string) => tabs.find((t) => t.key === dimension)?.label || dimension
const valuePlaceholder = (dimension: string) =>
  dimension === 'year' ? '四位数年份，如 2026' : dimension === 'venue' ? '小写会议标识，如 eccv' : '选项标识，如 poster'

const load = async () => {
  loading.value = true
  try {
    const data = (await getSiteOptions()) || []
    for (const tab of tabs) {
      lists[tab.key] = data.filter((o) => o.dimension === tab.key)
    }
  } finally {
    loading.value = false
  }
}

const saveOrder = async (dimension: string) => {
  await nextTick()
  await sortSiteOptions(lists[dimension].map((o) => o.id))
  ElMessage.success(`${dimensionName(dimension)}顺序已保存`)
  await load()
}

const openCreate = (dimension: string) => {
  Object.assign(form, { id: null, dimension, optionValue: '', labelZh: '', labelEn: '', sort: 0 })
  const list = lists[dimension] || []
  form.sort = list.length ? Math.max(...list.map((o) => o.sort || 0)) + 10 : 10
  dialogVisible.value = true
}

const openEdit = (row: any) => {
  Object.assign(form, row)
  dialogVisible.value = true
}

const save = async () => {
  if (!form.optionValue?.trim()) return ElMessage.warning('请填写选项值')
  saving.value = true
  try {
    const data = {
      id: form.id || undefined,
      dimension: form.dimension,
      optionValue: form.optionValue.trim(),
      labelZh: form.labelZh?.trim() || null,
      labelEn: form.labelEn?.trim() || null,
      sort: form.sort ?? 0
    }
    if (form.id) {
      await updateSiteOption(data)
    } else {
      await createSiteOption(data)
    }
    ElMessage.success('已保存，静态站配置已更新')
    dialogVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

const handleDelete = async (row: any) => {
  await ElMessageBox.confirm(
    `确定删除「${row.labelZh || row.optionValue}」？静态站上的筛选按钮会立即消失（图片不会被删除）。`,
    '删除确认',
    { type: 'warning' }
  )
  await deleteSiteOption(row.id)
  ElMessage.success('已删除')
  await load()
}

useRefreshOnActivate(load)
</script>

<style scoped>
.venue-sort {
  border: 1px solid #ebeef5;
  border-radius: 4px;
  overflow: hidden;
}
.venue-sort__row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  background: #fff;
  border-bottom: 1px solid #f2f3f5;
}
.venue-sort__row:last-child {
  border-bottom: none;
}
.venue-sort__row:hover {
  background: #f5f7fa;
}
.drag-handle {
  cursor: grab;
  color: #c0c4cc;
  font-size: 16px;
}
.drag-handle:active {
  cursor: grabbing;
}
.venue-sort__main {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 16px;
  min-width: 0;
}
.venue-sort__value {
  min-width: 80px;
  font-weight: 600;
  color: #303133;
}
.venue-sort__labels {
  font-size: 13px;
  color: #909399;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
