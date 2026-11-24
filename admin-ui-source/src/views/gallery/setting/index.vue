<template>
  <ContentWrap title="管线参数">
    <el-alert
      type="info"
      :closable="false"
      class="mb-12px"
      title="这些参数由后台传递给 gallery-pipeline（Python）。修改后新任务立即生效，正在运行的任务不受影响。"
    />
    <el-form label-width="220px" v-loading="loading">
      <el-form-item v-for="item in fields" :key="item.key" :label="item.label">
        <el-input v-model="form[item.key]" :placeholder="item.placeholder || ''" />
        <div class="text-12px text-gray-400 mt-2px">{{ item.key }}</div>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
        <el-button @click="load">重置</el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getSettings, updateSettings } from '@/api/gallery'

defineOptions({ name: 'GallerySetting' })

const fields = [
  { key: 'python', label: 'Python 解释器' },
  { key: 'workdir', label: '管线工作目录' },
  { key: 'site_dir', label: '静态站目录' },
  { key: 'dpi', label: '裁剪 DPI' },
  { key: 'max_pages', label: '扫描页数（0 = 全部页）' },
  { key: 'include_caption', label: '是否包含图注（true/false）' },
  { key: 'dup_hamming', label: '感知哈希重复阈值' },
  { key: 'min_confidence_auto', label: '高置信度提示阈值' },
  { key: 'publish_update_sitemap', label: '发布时更新 sitemap（true/false）' },
  { key: 'proxy', label: 'HTTP 代理（可选，OpenReview 用）', placeholder: 'http://127.0.0.1:7890' },
  { key: 'mineru_api_token', label: 'MinerU API Token', placeholder: '在线解析必填' },
  { key: 'mineru_api_base', label: 'MinerU API 地址', placeholder: 'https://mineru.net' },
  { key: 'mineru_model', label: 'MinerU 模型（pipeline/vlm）' },
  { key: 'mineru_batch_size', label: 'MinerU 批量提交数（≤50）' },
  { key: 'meili_enabled', label: '同步 Meilisearch 索引（true/false）' },
  { key: 'meili_sync_on_startup', label: '启动时同步索引（true/false）' },
  { key: 'meili_url', label: 'Meilisearch 地址', placeholder: 'http://127.0.0.1:7700' },
  { key: 'meili_index', label: '索引名', placeholder: 'figures' },
  { key: 'meili_api_key', label: 'Meilisearch API Key', placeholder: '需要 documents.add / settings.update 权限' }
]
const loading = ref(false)
const saving = ref(false)
const form = reactive<Record<string, string>>({})

const load = async () => {
  loading.value = true
  try {
    const data: any = await getSettings()
    fields.forEach((f) => (form[f.key] = data[f.key] ?? ''))
  } finally {
    loading.value = false
  }
}
const save = async () => {
  saving.value = true
  try {
    await updateSettings({ ...form })
    ElMessage.success('已保存')
  } finally {
    saving.value = false
  }
}
onMounted(load)
</script>
