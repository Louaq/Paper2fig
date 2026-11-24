<template>
  <el-form ref="formRef" :label-width="200" :model="formData" :rules="rules" @submit.prevent>
    <el-form-item label="头像">
      <div class="flex items-center gap-16px">
        <el-upload
          :show-file-list="false"
          :http-request="handleAvatarUpload"
          accept="image/png,image/jpeg,image/webp,image/gif"
        >
          <el-avatar :src="formData.avatar || defaultAvatar" :size="72" class="cursor-pointer" />
        </el-upload>
        <span class="text-12px text-gray-400">点击头像更换，支持 JPG / PNG，不超过 2MB</span>
      </div>
    </el-form-item>
    <el-form-item :label="t('profile.user.nickname')" prop="nickname">
      <el-input
        v-model="formData.nickname"
        :placeholder="t('common.inputText') + t('profile.user.nickname')"
        clearable
      />
    </el-form-item>
    <el-form-item :label="t('profile.user.mobile')" prop="mobile">
      <el-input
        v-model="formData.mobile"
        :placeholder="t('common.inputText') + t('profile.user.mobile')"
        clearable
      />
    </el-form-item>
    <el-form-item :label="t('profile.user.email')" prop="email">
      <el-input
        v-model="formData.email"
        :placeholder="t('common.inputText') + t('profile.user.email')"
        clearable
      />
    </el-form-item>
    <el-form-item :label="t('profile.user.sex')" prop="sex">
      <el-radio-group v-model="formData.sex">
        <el-radio :value="1">{{ t('profile.user.man') }}</el-radio>
        <el-radio :value="2">{{ t('profile.user.woman') }}</el-radio>
      </el-radio-group>
    </el-form-item>
  </el-form>
  <div style="text-align: center">
    <el-button type="primary" @click="submit()">{{ t('common.save') }}</el-button>
    <el-button type="danger" @click="init()">{{ t('common.reset') }}</el-button>
  </div>
</template>
<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus'
import { cloneDeep } from 'lodash-es'
import {
  getUserProfile,
  updateUserProfile,
  UserProfileUpdateReqVO
} from '@/api/system/user/profile'
import { useUserStore } from '@/store/modules/user'
import { updateFile } from '@/api/infra/file'
import defaultAvatar from '@/assets/imgs/avatar.gif'

defineOptions({ name: 'BasicInfo' })

const { t } = useI18n()
const message = useMessage() // 消息弹窗
const userStore = useUserStore()

// 定义事件
const emit = defineEmits<{
  (e: 'success'): void
}>()

// 表单校验（提示文案在校验时按当前语言取值，不刷新切换语言后同样生效）
const rules = reactive<FormRules>({
  nickname: [{ required: true, message: () => t('profile.rules.nickname'), trigger: 'blur' }],
  email: [
    { required: true, message: () => t('profile.rules.mail'), trigger: 'blur' },
    {
      type: 'email',
      message: () => t('profile.rules.truemail'),
      trigger: ['blur', 'change']
    }
  ],
  mobile: [
    { required: true, message: () => t('profile.rules.phone'), trigger: 'blur' },
    {
      pattern: /^1[3-9]\d{9}$/,
      message: () => t('profile.rules.truephone'),
      trigger: 'blur'
    }
  ]
})
// 表单数据：除可编辑字段外，保留接口返回的其它字段（如 avatar），提交时一并传回
const formData = ref<Recordable>({
  nickname: '',
  mobile: '',
  email: '',
  sex: 0
})
const formRef = ref<FormInstance>() // 表单 Ref

// 监听 userStore 中头像的变化，同步更新表单数据
watch(
  () => userStore.getUser.avatar,
  (newAvatar) => {
    if (newAvatar) {
      formData.value.avatar = newAvatar
    }
  }
)

/** 上传头像：先传文件拿到 URL，再保存到个人资料，最后同步顶栏头像 */
const handleAvatarUpload = async (options: any) => {
  const file: File = options.file
  if (!file) return
  if (!/^image\//.test(file.type)) {
    message.error('请选择图片文件')
    return
  }
  if (file.size > 2 * 1024 * 1024) {
    message.error('图片不能超过 2MB')
    return
  }
  const data = new FormData()
  // 文件名加时间戳，避免同名覆盖与文件列表里出现重复记录
  const ext = (file.name.split('.').pop() || 'png').toLowerCase()
  data.append('file', new File([file], `avatar_${Date.now()}.${ext}`, { type: file.type }))
  const res: any = await updateFile(data)
  const url = typeof res === 'string' ? res : res?.data
  if (!url) {
    message.error('上传失败，请稍后重试')
    return
  }
  formData.value.avatar = url
  await updateUserProfile({ ...(formData.value as UserProfileUpdateReqVO) })
  await userStore.setUserAvatarAction(url)
  message.success('头像已更新')
}

const submit = () => {
  const elForm = unref(formRef)
  if (!elForm) return
  elForm.validate(async (valid) => {
    if (valid) {
      const data = formData.value as UserProfileUpdateReqVO
      await updateUserProfile(data)
      message.success(t('common.updateSuccess'))
      const profile = await init()
      await userStore.setUserNicknameAction(profile.nickname)
      // 发送成功事件
      emit('success')
    }
  })
}

const init = async () => {
  const res = await getUserProfile()
  // 合并接口返回的全部字段（隔离接口对象，避免编辑时改动原数据）
  Object.assign(formData.value, cloneDeep(res))
  return res
}

onMounted(async () => {
  await init()
})
</script>
