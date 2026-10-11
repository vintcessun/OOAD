<template>
  <div>
    <el-card class="page-card">
      <div class="toolbar">
        <DeptTreeSelect v-model="query.departmentId" placeholder="全部部门" />
        <el-checkbox v-model="query.includeDescendants">含下级</el-checkbox>
        <el-input v-model="query.keyword" placeholder="用户名 / 姓名 / 工号" clearable style="width: 200px"
                  data-test="user-keyword" @keyup.enter="search" />
        <el-select v-model="query.status" placeholder="状态（默认不含已删除）" clearable style="width: 190px">
          <el-option v-for="(v, k) in USER_STATUS_LABEL" :key="k" :label="v" :value="k" />
        </el-select>
        <el-select v-model="query.userType" placeholder="类型" clearable style="width: 120px">
          <el-option v-for="(v, k) in USER_TYPE_LABEL" :key="k" :label="v" :value="k" />
        </el-select>
        <el-button type="primary" @click="search" data-test="user-search">搜索</el-button>
        <el-button @click="reset">重置</el-button>
      </div>
      <div class="toolbar">
        <el-button v-perm="'rbac:user:create'" type="primary" @click="openCreate" data-test="user-create">+ 新增用户</el-button>
        <el-button v-perm="'rbac:user:edit'" :disabled="!selected.length" @click="batchStatus('ACTIVE')">批量启用</el-button>
        <el-button v-perm="'rbac:user:edit'" :disabled="!selected.length" @click="batchStatus('DISABLED')">批量停用</el-button>
        <el-button v-perm="'rbac:user:delete'" :disabled="!selected.length" type="danger" plain
                   @click="batchStatus('DELETED')">批量删除</el-button>
        <span class="spacer" />
        <el-button v-perm="'rbac:assignment:assign'" @click="openBatch(false)" data-test="open-batch">批量授权</el-button>
        <el-button v-perm="'rbac:assignment:revoke'" @click="openBatch(true)">批量回收</el-button>
      </div>
      <el-table v-loading="loading" :data="page.items" @selection-change="selected = $event" data-test="user-table">
        <template #empty>你的管理范围内没有符合条件的用户，或尚未导入组织数据</template>
        <el-table-column type="selection" width="44" />
        <el-table-column label="用户名" width="130">
          <template #default="{ row }">
            <el-link type="primary" @click="router.push(`/users/${row.id}`)">{{ row.username }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="realName" label="姓名" width="110" />
        <el-table-column prop="departmentName" label="部门" min-width="200" />
        <el-table-column label="岗位" width="110">
          <template #default="{ row }">{{ row.position ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="电话" width="130">
          <template #default="{ row }">{{ maskPhone(row.phone) }}</template>
        </el-table-column>
        <el-table-column label="类型" width="90">
          <template #default="{ row }">{{ label(USER_TYPE_LABEL, row.userType) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'ACTIVE' ? 'success' : row.status === 'DISABLED' ? 'warning' : 'info'">
              {{ label(USER_STATUS_LABEL, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status !== 'DELETED'">
              <el-button v-perm="'rbac:user:edit'" link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button v-if="row.status === 'ACTIVE'" v-perm="'rbac:user:edit'" link type="warning"
                         @click="changeStatus(row, 'DISABLED')">停用</el-button>
              <el-button v-else v-perm="'rbac:user:edit'" link type="success" @click="changeStatus(row, 'ACTIVE')">启用</el-button>
              <el-button v-perm="'rbac:user:delete'" link type="danger" @click="remove(row)">删除</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination v-model:current-page="pageNo" v-model:page-size="query.size" :total="page.total"
                       :page-sizes="[20, 50, 100]" layout="total, sizes, prev, pager, next" @change="load" />
      </div>
    </el-card>

    <UserFormDialog v-if="formVisible" v-model="formVisible" :user="editing" @done="load" />
    <BatchAssignDialog v-if="batchVisible" v-model="batchVisible" :revoke="batchRevoke"
                       :user-ids="selected.map((u) => u.id)" :initial-dept-id="query.departmentId" @done="load" />
    <el-dialog v-model="statusResultVisible" title="批量操作结果" width="560px">
      <BatchResultView v-if="statusResult" :result="statusResult" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { userApi, type UserQuery } from '@/api'
import type { BatchResult, Page, User, UserStatus } from '@/api/types'
import { USER_STATUS_LABEL, USER_TYPE_LABEL, label, maskPhone } from '@/utils/format'
import DeptTreeSelect from '@/components/DeptTreeSelect.vue'
import BatchAssignDialog from '@/components/BatchAssignDialog.vue'
import BatchResultView from '@/components/BatchResultView.vue'
import UserFormDialog from '@/components/UserFormDialog.vue'

/** 用户列表（docs/05-ui.md §3.2）：只列出管理范围内的用户，由后端按操作者的适用部门过滤。 */
const router = useRouter()
const query = reactive<UserQuery & { size: number }>({ keyword: '', departmentId: null, includeDescendants: true,
  status: '', userType: '', size: 20 })
const pageNo = ref(1)
const page = ref<Page<User>>({ items: [], total: 0, page: 0, size: 20 })
const loading = ref(false)
const selected = ref<User[]>([])
const formVisible = ref(false)
const editing = ref<User | null>(null)
const batchVisible = ref(false)
const batchRevoke = ref(false)
const statusResult = ref<BatchResult | null>(null)
const statusResultVisible = ref(false)

// 只采用最后一次请求的结果：先发出的慢请求晚到时不能覆盖新的搜索结果
let seq = 0

async function load() {
  const mine = ++seq
  loading.value = true
  try {
    const result = await userApi.list({ ...query, page: pageNo.value - 1 })
    if (mine === seq) page.value = result
  } finally {
    if (mine === seq) loading.value = false
  }
}

function search() {
  pageNo.value = 1
  load()
}

function reset() {
  Object.assign(query, { keyword: '', departmentId: null, includeDescendants: true, status: '', userType: '' })
  search()
}

function openCreate() {
  editing.value = null
  formVisible.value = true
}

function openEdit(u: User) {
  editing.value = u
  formVisible.value = true
}

function openBatch(revoke: boolean) {
  batchRevoke.value = revoke
  batchVisible.value = true
}

async function changeStatus(u: User, status: 'ACTIVE' | 'DISABLED') {
  const verb = status === 'DISABLED' ? '停用' : '启用'
  const { value } = await ElMessageBox.prompt(
    status === 'DISABLED' ? `停用 ${u.realName}？停用后他手中的令牌在下一次请求即失效。` : `启用 ${u.realName}？`,
    verb, { inputPlaceholder: '原因（可选）' })
  await userApi.changeStatus(u.id, status, value || undefined)
  ElMessage.success(`已${verb}`)
  load()
}

async function remove(u: User) {
  const { value } = await ElMessageBox.prompt(
    `删除 ${u.realName}（${u.username}）？删除后用户名不能复用，已有指派保留供审计。`, '删除用户',
    { inputPlaceholder: '原因（可选）', type: 'warning' })
  await userApi.remove(u.id, value || undefined)
  ElMessage.success('已删除')
  load()
}

async function batchStatus(status: UserStatus) {
  const verb = { ACTIVE: '启用', DISABLED: '停用', DELETED: '删除' }[status]
  await ElMessageBox.confirm(`对选中的 ${selected.value.length} 人执行「${verb}」？逐个校验，不通过的会列出原因。`,
    `批量${verb}`, { type: 'warning' })
  statusResult.value = await userApi.batchStatus(selected.value.map((u) => u.id), status)
  statusResultVisible.value = true
  load()
}

onMounted(load)
</script>
