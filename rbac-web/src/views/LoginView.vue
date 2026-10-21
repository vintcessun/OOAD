<template>
  <div class="center">
    <el-card class="box">
      <h2>统一授权中心</h2>
      <el-form :model="form" @submit.prevent="submit" label-position="top">
        <el-form-item label="用户名">
          <el-input v-model="form.username" autocomplete="username" data-test="username" />
        </el-form-item>
        <el-form-item label="口令">
          <el-input v-model="form.password" type="password" show-password autocomplete="current-password"
                    data-test="password" @keyup.enter="submit" />
        </el-form-item>
        <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon data-test="login-error" />
        <el-button type="primary" class="btn" :loading="loading" native-type="submit" data-test="login">登录</el-button>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import type { ApiError } from '@/api/http'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()
const form = reactive({ username: '', password: '' })
const loading = ref(false)
const error = ref('')

/** 登录失败的提示：不区分用户不存在与口令错误（docs/05-ui.md §3.0）。 */
const MESSAGES: Record<number, string> = {
  40102: '用户名或口令错误',
  40103: '连续登录失败次数过多，账号已锁定 15 分钟',
  40104: '账号已停用，请联系管理员',
}

async function submit() {
  if (!form.username || !form.password) {
    error.value = '请输入用户名和口令'
    return
  }
  loading.value = true
  error.value = ''
  try {
    const r = await auth.login(form.username, form.password)
    if (r.mustChangePwd) {
      await router.push('/password')
    } else {
      await router.push((route.query.redirect as string) || '/')
    }
  } catch (e) {
    const err = e as ApiError
    error.value = MESSAGES[err.code] ?? err.message
  } finally {
    loading.value = false
  }
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
  width: 360px;
}
.btn {
  width: 100%;
  margin-top: 16px;
}
</style>
