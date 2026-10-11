<template>
  <div v-if="detail">
    <el-page-header class="page-card" @back="router.push('/roles')" title="角色列表" :content="detail.role.roleName" />
    <el-card class="page-card">
      <el-descriptions :column="3" border>
        <el-descriptions-item label="角色名称">{{ detail.role.roleName }}</el-descriptions-item>
        <el-descriptions-item label="角色编码"><span class="mono">{{ detail.role.roleCode }}</span></el-descriptions-item>
        <el-descriptions-item label="状态">{{ label(ROLE_STATUS_LABEL, detail.role.status) }}</el-descriptions-item>
        <el-descriptions-item label="范围">{{ detail.role.companyId === null ? '全局' : '公司内' }}</el-descriptions-item>
        <el-descriptions-item label="内置">{{ detail.role.builtin ? '是' : '否' }}</el-descriptions-item>
        <el-descriptions-item label="用户数">{{ detail.role.userCount }}</el-descriptions-item>
        <el-descriptions-item label="说明" :span="3">{{ detail.role.description ?? '—' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>
    <el-card>
      <el-tabs v-model="tab">
        <el-tab-pane :label="`权限 (${detail.permissions.length})`" name="perms">
          <el-alert v-if="isSuper" type="warning" :closable="false" show-icon class="gap"
                    title="超级管理员越过权限引擎，不需要也不接受授权" />
          <div class="toolbar">
            <el-input v-model="permKeyword" placeholder="权限码 / 资源" clearable style="width: 240px" />
            <span class="spacer" />
            <el-button v-perm="'rbac:grant:assign'" type="primary" :disabled="isSuper" @click="grantVisible = true"
                       data-test="open-grant">+ 授予权限</el-button>
          </div>
          <el-table :data="pagedPerms" data-test="role-perms">
            <el-table-column label="权限码">
              <template #default="{ row }"><PermissionCode :code="row.permissionCode" /></template>
            </el-table-column>
            <el-table-column prop="resourceName" label="资源" />
            <el-table-column prop="actionName" label="操作" width="80" />
            <el-table-column label="只限本人" width="90">
              <template #default="{ row }">{{ row.selfOnly ? '✓' : '' }}</template>
            </el-table-column>
            <el-table-column label="操作" width="80">
              <template #default="{ row }">
                <el-button v-perm="'rbac:grant:revoke'" link type="danger" @click="ungrant(row)">回收</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pager">
            <el-pagination v-model:current-page="permPage" :page-size="50" :total="filteredPerms.length"
                           layout="total, prev, pager, next" />
          </div>
        </el-tab-pane>
        <el-tab-pane :label="`用户 (${users.total})`" name="users">
          <el-table :data="users.items" data-test="role-users">
            <el-table-column label="用户" width="220">
              <template #default="{ row }">
                <el-link type="primary" @click="router.push(`/users/${row.userId}`)">{{ row.userRealName }}</el-link>
                <span class="mono muted"> {{ row.username }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="scopeDeptName" label="适用部门（含下级）" />
            <el-table-column prop="grantedBy" label="授予人" width="80" />
            <el-table-column label="授予时间" width="170">
              <template #default="{ row }">{{ formatTime(row.grantedAt) }}</template>
            </el-table-column>
          </el-table>
          <p class="muted">只显示你管理范围内的指派。</p>
          <div class="pager">
            <el-pagination v-model:current-page="userPage" :page-size="20" :total="users.total"
                           layout="total, prev, pager, next" @current-change="loadUsers" />
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>
    <GrantPermissionDialog v-if="grantVisible" v-model="grantVisible" :role-id="detail.role.id" @done="load" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { roleApi } from '@/api'
import type { Assignment, Page, RoleDetail, RolePermission } from '@/api/types'
import { ROLE_STATUS_LABEL, formatTime, label } from '@/utils/format'
import PermissionCode from '@/components/PermissionCode.vue'
import GrantPermissionDialog from '@/components/GrantPermissionDialog.vue'

/** 角色详情（docs/05-ui.md §3.5）：基本信息、权限（授予与回收）、用户。 */
const route = useRoute()
const router = useRouter()
const id = Number(route.params.id)
const detail = ref<RoleDetail | null>(null)
const users = ref<Page<Assignment>>({ items: [], total: 0, page: 0, size: 20 })
const tab = ref((route.query.tab as string) || 'perms')
const permKeyword = ref('')
const permPage = ref(1)
const userPage = ref(1)
const grantVisible = ref(false)

const isSuper = computed(() => detail.value?.role.roleCode === 'SUPER_ADMIN')
const filteredPerms = computed(() => {
  const k = permKeyword.value.trim()
  return (detail.value?.permissions ?? []).filter((p) => !k || p.permissionCode.includes(k) || p.resourceName.includes(k))
})
const pagedPerms = computed(() => filteredPerms.value.slice((permPage.value - 1) * 50, permPage.value * 50))

async function load() {
  detail.value = await roleApi.detail(id)
  await loadUsers()
}

async function loadUsers() {
  users.value = await roleApi.users(id, userPage.value - 1, 20)
}

async function ungrant(p: RolePermission) {
  await ElMessageBox.confirm(`回收 ${p.permissionCode}？持有该角色的 ${detail.value!.role.userCount} 名用户，下一次请求即失去该权限。`,
    '回收权限', { type: 'warning' })
  await roleApi.ungrant(id, p.permissionId)
  ElMessage.success('已回收')
  load()
}

onMounted(load)
</script>

<style scoped>
.gap {
  margin-bottom: 12px;
}
</style>
