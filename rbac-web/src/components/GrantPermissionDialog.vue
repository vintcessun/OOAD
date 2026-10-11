<template>
  <el-dialog v-model="visible" title="授予权限" width="760px" destroy-on-close data-test="grant-dialog">
    <div class="toolbar">
      <el-select v-model="systemId" placeholder="业务系统" clearable style="width: 200px" @change="search">
        <el-option v-for="s in systems" :key="s.id" :label="s.systemName" :value="s.id" />
      </el-select>
      <el-input v-model="keyword" placeholder="权限码 / 资源名" clearable style="width: 220px" @keyup.enter="search"
                data-test="grant-keyword" />
      <el-button @click="search" data-test="grant-search">查询</el-button>
      <span class="spacer" />
      <span class="muted">已选 {{ chosen.size }} 个</span>
    </div>
    <el-table ref="tableRef" :data="page.items" height="360" row-key="id" @select="onSelect" @select-all="onSelectAll"
              data-test="grant-table">
      <el-table-column type="selection" width="44" reserve-selection />
      <el-table-column label="权限码">
        <template #default="{ row }"><PermissionCode :code="row.permissionCode" /></template>
      </el-table-column>
      <el-table-column prop="resourceName" label="资源" />
      <el-table-column prop="actionName" label="操作" width="70" />
    </el-table>
    <div class="pager">
      <el-pagination v-model:current-page="pageNo" :page-size="50" :total="page.total" layout="total, prev, pager, next"
                     @current-change="load" />
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :disabled="!chosen.size" :loading="loading" @click="submit" data-test="grant-submit">授予</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { catalogApi, roleApi } from '@/api'
import type { ApiError } from '@/api/http'
import type { Page, PermissionView, SystemView } from '@/api/types'
import PermissionCode from './PermissionCode.vue'

/**
 * 为角色授予权限。权限按系统 / 关键词分页查询（共 1,800 余个，不一次全取），跨页勾选后一次提交。
 * 后端返回 40303 时说明「不能授出你自己没有的权限」（docs/05-ui.md §3.5）。
 */
const visible = defineModel<boolean>({ default: false })
const props = defineProps<{ roleId: number }>()
const emit = defineEmits<{ (e: 'done'): void }>()

const systems = ref<SystemView[]>([])
const systemId = ref<number>()
const keyword = ref('')
const pageNo = ref(1)
const page = ref<Page<PermissionView>>({ items: [], total: 0, page: 0, size: 50 })
const chosen = ref(new Set<number>())
const loading = ref(false)
const error = ref('')

async function load() {
  page.value = await catalogApi.permissions({ systemId: systemId.value, keyword: keyword.value, page: pageNo.value - 1, size: 50 })
}

function search() {
  pageNo.value = 1
  load()
}

function onSelect(_rows: PermissionView[], row: PermissionView) {
  if (chosen.value.has(row.id)) chosen.value.delete(row.id)
  else chosen.value.add(row.id)
}

function onSelectAll(rows: PermissionView[]) {
  const all = rows.length > 0
  page.value.items.forEach((r) => (all ? chosen.value.add(r.id) : chosen.value.delete(r.id)))
}

async function submit() {
  loading.value = true
  error.value = ''
  try {
    await roleApi.grant(props.roleId, [...chosen.value])
    visible.value = false
    emit('done')
  } catch (e) {
    // 40303：后端说明里带着第一个授不出的权限码
    error.value = (e as ApiError).message
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  systems.value = await catalogApi.systems()
  await load()
})
</script>
