<template>
  <ContentWrap>
    <el-form :inline="true" :model="query" class="mb-10px">
      <el-form-item label="状态">
        <el-radio-group v-model="query.status" @change="handleQuery">
          <el-radio-button :value="0">待审核</el-radio-button>
          <el-radio-button :value="1">已通过</el-radio-button>
          <el-radio-button :value="2">已拒绝</el-radio-button>
          <el-radio-button :value="null">全部</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="置信度">
        <el-input-number v-model="query.minConfidence" :min="0" :max="1" :step="0.05" controls-position="right" class="!w-110px" />
        <span class="mx-6px">~</span>
        <el-input-number v-model="query.maxConfidence" :min="0" :max="1" :step="0.05" controls-position="right" class="!w-110px" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleQuery"><Icon icon="ep:search" class="mr-5px" />查询</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" />重置</el-button>
        <el-button type="success" plain :disabled="!selected.length" @click="batchApprove" v-hasPermi="['gallery:review:approve']">
          批量通过（{{ selected.length }}）
        </el-button>
      </el-form-item>
    </el-form>

    <div v-loading="loading" class="grid grid-cols-2 gap-14px xl:grid-cols-3">
      <el-card v-for="item in list" :key="item.id" shadow="hover" :body-style="{ padding: '10px' }">
        <div class="relative">
          <el-image
            :src="item.thumb"
            :preview-src-list="[item.thumb]"
            fit="contain"
            class="w-full bg-gray-50"
            style="height: 210px"
            preview-teleported
          />
          <el-checkbox
            v-model="checked"
            :label="item.id"
            class="!absolute !left-6px !top-6px !bg-white/80 !px-6px !rounded"
          />
          <el-tag
            class="!absolute !right-6px !top-6px"
            :type="item.confidence >= 0.8 ? 'success' : item.confidence >= 0.55 ? 'warning' : 'danger'"
          >{{ (item.confidence * 100).toFixed(0) }}%</el-tag>
        </div>
        <div class="mt-8px text-13px font-medium leading-5" :title="item.title">
          {{ item.title.length > 70 ? item.title.slice(0, 70) + '…' : item.title }}
        </div>
        <div class="text-12px text-gray-500 mt-4px">
          {{ item.venue.toUpperCase() }} {{ item.year }} · P{{ item.pageNo }} · {{ item.width }}×{{ item.height }}
          · 算法 {{ item.algorithm }}
        </div>
        <div class="mt-4px flex flex-wrap gap-4px">
          <el-tag v-for="f in flags(item)" :key="f" size="small" type="danger" effect="plain">{{ f }}</el-tag>
          <el-tag v-if="!flags(item).length" size="small" type="success" effect="plain">质检通过</el-tag>
        </div>
        <div class="mt-8px flex items-center gap-6px">
          <el-select v-model="item.pattern" size="small" class="!w-120px">
            <el-option v-for="p in patterns" :key="p" :label="p" :value="p" />
          </el-select>
          <el-button type="success" size="small" :disabled="item.status !== 0" @click="approve(item)" v-hasPermi="['gallery:review:approve']">通过</el-button>
          <el-button type="danger" size="small" plain :disabled="item.status !== 0" @click="reject(item)" v-hasPermi="['gallery:review:approve']">拒绝</el-button>
          <el-button size="small" plain @click="requeue(item)" v-hasPermi="['gallery:review:approve']">重裁</el-button>
          <el-button type="danger" size="small" plain @click="deleteCrop(item)" v-hasPermi="['gallery:review:approve']">删图</el-button>
          <el-link :href="item.paperUrl" target="_blank" type="primary" class="text-12px ml-auto">原文 ↗</el-link>
        </div>
      </el-card>
    </div>

    <el-empty v-if="!loading && !list.length" description="没有待审核的裁剪结果" />

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
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  approveReview, batchApproveReview, deleteReviewCrop, getReviewPage, rejectReview, requeueReview
} from '@/api/gallery'
import { useRefreshOnActivate } from '@/hooks/web/useRefreshOnActivate'

defineOptions({ name: 'GalleryReview' })

const patterns = ['teaser', 'conceptual', 'framework', 'pipeline', 'architecture', 'taxonomy', 'results', 'comparison']
const loading = ref(false)
const list = ref<any[]>([])
const total = ref(0)
const checked = ref<number[]>([])
const query = reactive<any>({ pageNo: 1, pageSize: 9, status: 0, minConfidence: null, maxConfidence: null })

const selected = computed(() => checked.value)

const flags = (item: any) => {
  try {
    const parsed = JSON.parse(item.flagsJson || '[]')
    return Array.isArray(parsed) ? parsed : []
  } catch (e) {
    return []
  }
}

const load = async () => {
  loading.value = true
  try {
    const data = await getReviewPage({ ...query })
    list.value = data.list.map((x: any) => ({ ...x, pattern: x.pattern || 'teaser' }))
    total.value = data.total
  } finally {
    loading.value = false
  }
}
const handleQuery = () => { query.pageNo = 1; load() }
const resetQuery = () => {
  Object.assign(query, { pageNo: 1, status: 0, minConfidence: null, maxConfidence: null })
  load()
}

const approve = async (item: any) => {
  await approveReview({ id: item.id, pattern: item.pattern, tier: item.tier, award: item.award })
  ElMessage.success('已通过')
  await load()
}
const reject = async (item: any) => {
  const { value } = await ElMessageBox.prompt('拒绝原因（可留空）', '拒绝', { inputValue: '' }).catch(() => ({ value: null }))
  if (value === null) return
  await rejectReview({ id: item.id, comment: value })
  ElMessage.success('已拒绝')
  await load()
}
const requeue = async (item: any) => {
  await requeueReview({ id: item.id })
  ElMessage.success('已打回，等待重新裁剪')
  await load()
}
const deleteCrop = async (item: any) => {
  await ElMessageBox.confirm(
    '确定删除该裁剪图片？会同时移除裁剪记录；未发布的论文将回到待处理，已发布的不会影响静态站。',
    '删除裁剪图片',
    { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
  )
  await deleteReviewCrop(item.id)
  ElMessage.success('裁剪图片已删除')
  checked.value = checked.value.filter((id: number) => id !== item.id)
  await load()
}
const batchApprove = async () => {
  await batchApproveReview({ ids: checked.value, pattern: 'teaser' })
  ElMessage.success('批量通过完成')
  checked.value = []
  await load()
}

useRefreshOnActivate(load)
</script>
