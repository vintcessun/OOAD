<template>
  <div>
    <UserDetailPanel v-if="detail" :detail="detail">
      <template #actions>
        <el-button @click="router.push('/password')">修改口令</el-button>
      </template>
    </UserDetailPanel>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { authApi } from '@/api'
import type { UserDetail } from '@/api/types'
import UserDetailPanel from '@/components/UserDetailPanel.vue'

// 个人中心：本人的基本信息、角色、有效权限，只读（docs/05-ui.md §3.12）
const router = useRouter()
const detail = ref<UserDetail | null>(null)
onMounted(async () => {
  detail.value = await authApi.me()
})
</script>
