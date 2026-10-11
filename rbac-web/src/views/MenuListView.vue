<template>
  <el-card>
    <div class="toolbar">
      <span class="muted">菜单也是一种资源：显示与否只取决于用户是否持有「挂接资源:view」；修改菜单不会改变任何人的接口权限。</span>
      <span class="spacer" />
      <el-button v-perm="'rbac:menu:create'" type="primary" @click="openForm(null)" data-test="menu-create">+ 新增菜单</el-button>
    </div>
    <el-table :data="menus" row-key="id" default-expand-all :tree-props="{ children: 'children' }" data-test="menu-table">
      <el-table-column prop="menuName" label="名称" />
      <el-table-column label="路由">
        <template #default="{ row }"><span class="mono">{{ row.routePath ?? '—（目录）' }}</span></template>
      </el-table-column>
      <el-table-column label="挂接资源">
        <template #default="{ row }"><span class="mono">{{ row.resourceCode ?? '—' }}</span></template>
      </el-table-column>
      <el-table-column prop="componentKey" label="组件" />
      <el-table-column prop="sortOrder" label="排序" width="70" />
      <el-table-column label="操作" width="140">
        <template #default="{ row }">
          <el-button v-perm="'rbac:menu:edit'" link type="primary" @click="openForm(row)">编辑</el-button>
          <el-button v-perm="'rbac:menu:delete'" link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="visible" :title="editing ? '编辑菜单' : '新增菜单'" width="520px" data-test="menu-form">
      <el-form label-width="90px">
        <el-form-item label="上级菜单">
          <el-tree-select v-model="form.parentId" :data="menus" :props="{ label: 'menuName', children: 'children', value: 'id' }"
                          check-strictly clearable placeholder="留空为顶层" />
        </el-form-item>
        <el-form-item label="挂接资源">
          <el-select v-model="form.resourceId" clearable placeholder="留空即目录（只能选菜单类型资源）">
            <el-option v-for="r in menuResources" :key="r.id" :label="`${r.resourceName}（${r.resourceCode}）`" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="名称"><el-input v-model="form.menuName" data-test="menu-name" /></el-form-item>
        <el-form-item label="路由"><el-input v-model="form.routePath" placeholder="/users" /></el-form-item>
        <el-form-item label="组件键"><el-input v-model="form.componentKey" placeholder="UserList" /></el-form-item>
        <el-form-item label="图标"><el-input v-model="form.icon" placeholder="user" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sortOrder" :min="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="save" data-test="menu-save">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { catalogApi, menuApi } from '@/api'
import type { MenuNode, ResourceView, SaveMenu } from '@/api/types'
import { useAuthStore } from '@/stores/auth'

/** 菜单管理（docs/05-ui.md §3.7）。 */
const auth = useAuthStore()
const menus = ref<MenuNode[]>([])
const menuResources = ref<ResourceView[]>([])
const visible = ref(false)
const editing = ref<MenuNode | null>(null)
const form = reactive({ parentId: undefined as number | undefined, resourceId: undefined as number | undefined, menuName: '',
  routePath: '', componentKey: '', icon: '', sortOrder: 0 })

async function load() {
  menus.value = await menuApi.all()
}

function openForm(m: MenuNode | null) {
  editing.value = m
  Object.assign(form, { parentId: m?.parentId ?? undefined, resourceId: m?.resourceId ?? undefined, menuName: m?.menuName ?? '',
    routePath: m?.routePath ?? '', componentKey: m?.componentKey ?? '', icon: m?.icon ?? '', sortOrder: m?.sortOrder ?? 0 })
  visible.value = true
}

async function save() {
  const body: SaveMenu = { parentId: form.parentId ?? null, resourceId: form.resourceId ?? null, menuName: form.menuName,
    routePath: form.routePath || null, componentKey: form.componentKey || null, icon: form.icon || null, sortOrder: form.sortOrder }
  if (editing.value) await menuApi.update(editing.value.id, body)
  else await menuApi.create(body)
  ElMessage.success('已保存')
  visible.value = false
  await load()
  auth.loadedAt = 0 // 自己的菜单可能变了，下次切换页面时重新拉取
}

async function remove(m: MenuNode) {
  await ElMessageBox.confirm(`删除菜单「${m.menuName}」？有子菜单时不能删除。`, '删除菜单', { type: 'warning' })
  await menuApi.remove(m.id)
  ElMessage.success('已删除')
  await load()
  auth.loadedAt = 0
}

onMounted(async () => {
  await load()
  menuResources.value = (await catalogApi.resources({ type: 'MENU', size: 200 })).items
})
</script>
