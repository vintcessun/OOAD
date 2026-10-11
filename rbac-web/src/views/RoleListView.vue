<template>
  <el-card>
    <div class="toolbar">
      <el-input v-model="keyword" placeholder="编码 / 名称" clearable style="width: 200px" @keyup.enter="load" />
      <el-select v-model="status" placeholder="状态" clearable style="width: 120px" @change="load">
        <el-option label="启用" value="ENABLED" />
        <el-option label="停用" value="DISABLED" />
      </el-select>
      <el-button @click="load">搜索</el-button>
      <span class="spacer" />
      <el-button v-perm="'rbac:role:create'" type="primary" @click="openForm(null)" data-test="role-create">+ 新增角色</el-button>
    </div>
    <el-table v-loading="loading" :data="roles" data-test="role-list">
      <el-table-column label="角色名称" min-width="140">
        <template #default="{ row }">
          <el-link type="primary" @click="router.push(`/roles/${row.id}`)">{{ row.roleName }}</el-link>
        </template>
      </el-table-column>
      <el-table-column label="角色编码" min-width="160">
        <template #default="{ row }"><span class="mono">{{ row.roleCode }}</span></template>
      </el-table-column>
      <el-table-column label="范围" width="110">
        <template #default="{ row }">
          {{ row.companyId === null ? '全局' : companyName(row.companyId) }}<span v-if="row.builtin" title="内置，不能删除"> 🔒</span>
        </template>
      </el-table-column>
      <el-table-column prop="userCount" label="用户数" width="90" />
      <el-table-column prop="permissionCount" label="权限数" width="90" />
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag size="small" :type="row.status === 'ENABLED' ? 'success' : 'info'">{{ label(ROLE_STATUS_LABEL, row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200">
        <template #default="{ row }">
          <el-button link type="primary" @click="router.push(`/roles/${row.id}`)">详情</el-button>
          <el-button v-perm="'rbac:role:edit'" link type="primary" @click="openForm(row)">编辑</el-button>
          <el-button v-if="row.roleCode !== 'SUPER_ADMIN'" v-perm="'rbac:role:edit'" link type="warning" @click="toggle(row)">
            {{ row.status === 'ENABLED' ? '停用' : '启用' }}
          </el-button>
          <el-button v-if="!row.builtin" v-perm="'rbac:role:delete'" link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <p class="muted">「全局」= 内置角色与权限清单的五类角色模板，只有超级管理员能修改；公司内角色只能指派到本公司的部门。</p>

    <el-dialog v-model="formVisible" :title="editing ? '编辑角色' : '新增角色'" width="500px" data-test="role-form">
      <el-form label-width="90px">
        <el-form-item label="角色编码">
          <el-input v-model="form.roleCode" :disabled="!!editing" placeholder="大写字母开头，如 FIN_CLERK" data-test="role-code" />
        </el-form-item>
        <el-form-item label="角色名称">
          <el-input v-model="form.roleName" data-test="role-name" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.description" type="textarea" />
        </el-form-item>
        <el-form-item v-if="!editing" label="所属公司">
          <el-select v-model="form.companyId" clearable placeholder="留空 = 全局角色（仅超级管理员）" data-test="role-company">
            <el-option v-for="c in companies" :key="c.id" :label="c.companyName" :value="c.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" @click="save" data-test="role-save">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { orgApi, roleApi } from '@/api'
import type { CompanyView, Role } from '@/api/types'
import { ROLE_STATUS_LABEL, label } from '@/utils/format'

/** 角色列表（docs/05-ui.md §3.4）：角色名称、角色编码、用户数、权限数、状态。 */
const router = useRouter()
const roles = ref<Role[]>([])
const companies = ref<CompanyView[]>([])
const keyword = ref('')
const status = ref('')
const loading = ref(false)
const formVisible = ref(false)
const editing = ref<Role | null>(null)
const form = reactive({ roleCode: '', roleName: '', description: '', companyId: undefined as number | undefined })

const companyName = (id: number) => companies.value.find((c) => c.id === id)?.companyName ?? `公司 ${id}`

async function load() {
  loading.value = true
  try {
    roles.value = await roleApi.list(keyword.value, status.value)
  } finally {
    loading.value = false
  }
}

function openForm(r: Role | null) {
  editing.value = r
  Object.assign(form, { roleCode: r?.roleCode ?? '', roleName: r?.roleName ?? '', description: r?.description ?? '',
    companyId: r?.companyId ?? undefined })
  formVisible.value = true
}

async function save() {
  if (editing.value) {
    await roleApi.update(editing.value.id, { roleName: form.roleName, description: form.description })
  } else {
    await roleApi.create({ roleCode: form.roleCode, roleName: form.roleName, description: form.description || undefined,
      companyId: form.companyId })
  }
  ElMessage.success('已保存')
  formVisible.value = false
  load()
}

async function toggle(r: Role) {
  const next = r.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
  if (next === 'DISABLED') {
    await ElMessageBox.confirm(`停用「${r.roleName}」将影响 ${r.userCount} 名用户，他们下一次请求即失去该角色的全部权限。`,
      '停用角色', { type: 'warning' })
  }
  await roleApi.update(r.id, { status: next })
  ElMessage.success('已更新')
  load()
}

async function remove(r: Role) {
  await ElMessageBox.confirm(`删除角色「${r.roleName}」？仍有指派时不能删除，需先回收。`, '删除角色', { type: 'warning' })
  try {
    await roleApi.remove(r.id)
    ElMessage.success('已删除')
    load()
  } catch {
    // 409：仍有指派。提示已由统一错误处理弹出，这里给出去处
    router.push(`/roles/${r.id}?tab=users`)
  }
}

onMounted(async () => {
  companies.value = await orgApi.companies().catch(() => [])
  load()
})
</script>
