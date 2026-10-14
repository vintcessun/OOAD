<template>
  <div data-test="user-detail">
    <el-card class="page-card">
      <div class="head">
        <div>
          <span class="name">{{ detail.user.realName }}</span>
          <span class="mono muted">{{ detail.user.username }}</span>
          <el-tag v-if="detail.superAdmin" type="danger" class="tag">超级管理员：越过权限引擎</el-tag>
          <el-tag :type="statusType(detail.user.status)" class="tag">{{ label(USER_STATUS_LABEL, detail.user.status) }}</el-tag>
        </div>
        <div class="actions"><slot name="actions" /></div>
      </div>
    </el-card>

    <el-card>
      <el-tabs v-model="tab">
        <el-tab-pane label="基本信息" name="basic">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="用户名">{{ detail.user.username }}</el-descriptions-item>
            <el-descriptions-item label="姓名">{{ detail.user.realName }}</el-descriptions-item>
            <el-descriptions-item label="类型">{{ label(USER_TYPE_LABEL, detail.user.userType) }}</el-descriptions-item>
            <el-descriptions-item label="工号">{{ detail.user.employeeNo ?? '—' }}</el-descriptions-item>
            <el-descriptions-item label="所属部门">{{ detail.user.departmentName ?? '—' }}</el-descriptions-item>
            <el-descriptions-item label="岗位">{{ detail.user.position ?? '—' }}</el-descriptions-item>
            <el-descriptions-item label="电话">{{ detail.user.phone ?? '—' }}</el-descriptions-item>
            <el-descriptions-item label="到期时间">{{ formatTime(detail.user.expiresAt) }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              {{ label(USER_STATUS_LABEL, detail.user.status) }}
              <span v-if="detail.user.statusReason" class="muted">（{{ detail.user.statusReason }}）</span>
            </el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ formatTime(detail.user.createdAt) }}</el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>

        <el-tab-pane :label="`角色 (${detail.roles.length})`" name="roles">
          <div class="toolbar">
            <span class="muted">同一角色可在多个部门各指派一次；适用部门覆盖其全部下级部门</span>
            <span class="spacer" />
            <el-button v-if="manageable" v-perm="'rbac:assignment:assign'" type="primary" size="small"
                       @click="assignVisible = true" data-test="open-assign">分配角色</el-button>
          </div>
          <el-table :data="detail.roles" data-test="role-table">
            <el-table-column label="角色">
              <template #default="{ row }">
                {{ row.roleName }} <span class="mono muted">{{ row.roleCode }}</span>
                <el-tag v-if="row.roleStatus === 'DISABLED'" size="small" type="info">已停用</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="scopeDeptName" label="适用部门（含下级）" />
            <el-table-column prop="grantedBy" label="授予人" width="80" />
            <el-table-column label="授予时间" width="170">
              <template #default="{ row }">{{ formatTime(row.grantedAt) }}</template>
            </el-table-column>
            <el-table-column prop="reason" label="原因" />
            <el-table-column v-if="manageable" label="操作" width="90">
              <template #default="{ row }">
                <el-button v-perm="'rbac:assignment:revoke'" link type="danger" @click="revoke(row)"
                           data-test="revoke">回收</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane :label="`有效权限 (${detail.permissions.length})`" name="perms">
          <el-alert v-if="detail.superAdmin" type="warning" :closable="false" show-icon
                    title="超级管理员越过权限引擎：只要账号有效，任何权限都放行，因此这里不逐条列出" class="gap" />
          <div class="toolbar">
            <el-input v-model="keyword" placeholder="权限码 / 角色 / 部门" clearable style="width: 240px" />
            <el-select v-model="system" placeholder="业务系统" clearable style="width: 160px">
              <el-option v-for="s in systems" :key="s" :label="s" :value="s" />
            </el-select>
            <el-checkbox v-model="onlySelf">只看「只限本人」</el-checkbox>
            <span class="spacer" />
            <el-button v-if="canExplain" link type="primary" data-test="explain-link" @click="router.push({ path: '/explain', query: { userId: detail.user.id } })">
              解释某个权限 →
            </el-button>
          </div>
          <el-table :data="pagedPerms" data-test="perm-table">
            <el-table-column label="权限码">
              <template #default="{ row }"><PermissionCode :code="row.permissionCode" /></template>
            </el-table-column>
            <el-table-column prop="roleName" label="来源角色" width="160" />
            <el-table-column prop="scopeDeptName" label="适用部门（含下级）" />
            <el-table-column label="只限本人" width="90">
              <template #default="{ row }">{{ row.selfOnly ? '✓' : '' }}</template>
            </el-table-column>
          </el-table>
          <div class="pager">
            <el-pagination v-model:current-page="permPage" :page-size="50" :total="filteredPerms.length"
                           layout="total, prev, pager, next" />
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>
    <AssignRoleDialog v-if="assignVisible" v-model="assignVisible" :user-id="detail.user.id"
                      :company-id="detail.user.companyId" @done="emit('changed')" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import { userApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import type { Assignment, UserDetail } from '@/api/types'
import { USER_STATUS_LABEL, USER_TYPE_LABEL, formatTime, label } from '@/utils/format'
import PermissionCode from './PermissionCode.vue'
import AssignRoleDialog from './AssignRoleDialog.vue'

/**
 * 用户详情：基本信息、角色、有效权限（docs/05-ui.md §3.3）。个人中心与用户详情页共用。
 * 每条有效权限都带来源角色与适用部门；同一权限码的多行不合并，否则看不出作用域。
 */
const props = defineProps<{ detail: UserDetail; manageable?: boolean; initialTab?: string }>()
const emit = defineEmits<{ (e: 'changed'): void }>()
const router = useRouter()

const tab = ref(props.initialTab ?? 'basic')

/**
 * 「解释某个权限」只给能用上它的人：要有「权限解释」菜单；解释别人还要 rbac:authz:explain
 * （ExplainView 选别人还要 rbac:user:view，能打开用户详情页的人已经有）。后端仍按被查用户所在部门独立判定。
 */
const auth = useAuthStore()
const canExplain = computed(() => auth.has('rbac:explain:view')
  && (props.detail.user.id === auth.me?.user.id || auth.has('rbac:authz:explain')))

const assignVisible = ref(false)
const keyword = ref('')
const system = ref('')
const onlySelf = ref(false)
const permPage = ref(1)

const systems = computed(() => [...new Set(props.detail.permissions.map((p) => p.permissionCode.split(':')[0]))].sort())
const filteredPerms = computed(() => {
  const k = keyword.value.trim()
  return props.detail.permissions.filter((p) =>
    (!k || p.permissionCode.includes(k) || p.roleName.includes(k) || p.scopeDeptName.includes(k))
    && (!system.value || p.permissionCode.startsWith(system.value + ':'))
    && (!onlySelf.value || p.selfOnly))
})
const pagedPerms = computed(() => filteredPerms.value.slice((permPage.value - 1) * 50, permPage.value * 50))

const statusType = (s: string) => (s === 'ACTIVE' ? 'success' : s === 'DISABLED' ? 'warning' : 'info')

async function revoke(row: Assignment) {
  await ElMessageBox.confirm(
    `回收「${row.roleName} @ ${row.scopeDeptName}」？该用户下一次请求即失去这一指派带来的权限。`,
    '回收角色', { type: 'warning' })
  await userApi.revoke(props.detail.user.id, row.assignmentId)
  ElMessage.success('已回收')
  emit('changed')
}
</script>

<style scoped>
.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.name {
  font-size: 18px;
  font-weight: 600;
  margin-right: 8px;
}
.tag {
  margin-left: 8px;
}
.gap {
  margin-bottom: 12px;
}
</style>
