<template>
  <div>
    <el-page-header class="page-card" @back="router.push('/users')" title="用户列表" :content="detail?.user.realName ?? ''" />
    <UserDetailPanel v-if="detail" :detail="detail" manageable :initial-tab="'roles'" @changed="load">
      <template #actions>
        <template v-if="detail.user.status !== 'DELETED'">
          <el-button v-perm="'rbac:user:edit'" @click="editVisible = true">编辑</el-button>
          <el-button v-if="detail.user.status === 'ACTIVE'" v-perm="'rbac:user:edit'" type="warning" plain
                     @click="toggle('DISABLED')">停用</el-button>
          <el-button v-else v-perm="'rbac:user:edit'" type="success" plain @click="toggle('ACTIVE')">启用</el-button>
        </template>
      </template>
    </UserDetailPanel>
    <UserFormDialog v-if="editVisible && detail" v-model="editVisible" :user="detail.user" @done="load" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { userApi } from '@/api'
import type { UserDetail } from '@/api/types'
import UserDetailPanel from '@/components/UserDetailPanel.vue'
import UserFormDialog from '@/components/UserFormDialog.vue'

/** 用户详情（docs/05-ui.md §3.3）：基本信息、角色（可分配、回收）、有效权限。 */
const route = useRoute()
const router = useRouter()
const detail = ref<UserDetail | null>(null)
const editVisible = ref(false)

async function load() {
  detail.value = await userApi.detail(Number(route.params.id))
}

async function toggle(status: 'ACTIVE' | 'DISABLED') {
  const { value } = await ElMessageBox.prompt(status === 'DISABLED' ? '停用后该用户的令牌在下一次请求即失效。' : '启用该用户？',
    status === 'DISABLED' ? '停用' : '启用', { inputPlaceholder: '原因（可选）' })
  await userApi.changeStatus(detail.value!.user.id, status, value || undefined)
  ElMessage.success('已更新')
  load()
}

onMounted(load)
</script>
