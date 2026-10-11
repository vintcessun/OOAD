<template>
  <div class="center">
    <el-card class="box">
      <h2>{{ auth.mustChangePwd ? '首次登录，请修改口令' : '修改口令' }}</h2>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
        <el-form-item label="旧口令" prop="oldPassword">
          <el-input v-model="form.oldPassword" type="password" show-password data-test="old-password" />
        </el-form-item>
        <el-form-item label="新口令" prop="newPassword">
          <el-input v-model="form.newPassword" type="password" show-password data-test="new-password" />
        </el-form-item>
        <el-form-item label="确认新口令" prop="confirm">
          <el-input v-model="form.confirm" type="password" show-password data-test="confirm-password" />
        </el-form-item>
        <div class="muted">至少 8 位，含大写、小写、数字与特殊字符，且不同于旧口令。修改后需要重新登录。</div>
        <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="gap" />
        <div class="gap">
          <el-button type="primary" native-type="submit" :loading="loading" data-test="change-password">确认修改</el-button>
          <el-button v-if="!auth.mustChangePwd" @click="router.back()">返回</el-button>
          <el-button v-else link @click="logout">退出登录</el-button>
        </div>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { authApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import type { ApiError } from '@/api/http'
import { meetsPolicy } from '@/utils/password'

const auth = useAuthStore()
const router = useRouter()
const formRef = ref<FormInstance>()
const form = reactive({ oldPassword: '', newPassword: '', confirm: '' })
const loading = ref(false)
const error = ref('')

const rules: FormRules = {
  oldPassword: [{ required: true, message: '请输入旧口令' }],
  newPassword: [
    { required: true, message: '请输入新口令' },
    { validator: (_r, v, cb) => (meetsPolicy(v) ? cb() : cb(new Error('口令不符合策略'))) },
  ],
  confirm: [{ validator: (_r, v, cb) => (v === form.newPassword ? cb() : cb(new Error('两次输入不一致'))) }],
}

async function submit() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  loading.value = true
  error.value = ''
  try {
    await authApi.changePassword(form.oldPassword, form.newPassword)
    ElMessage.success('口令已修改，请用新口令重新登录')
    auth.clear() // 改口令后此前签发的令牌全部作废
    router.push('/login')
  } catch (e) {
    error.value = (e as ApiError).message
  } finally {
    loading.value = false
  }
}

async function logout() {
  await auth.logout()
  router.push('/login')
}
</script>

<style scoped>
.center {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}
.box {
  width: 400px;
}
.gap {
  margin-top: 12px;
}
</style>
