<template>
  <div class="dept-select">
    <el-select v-model="companyId" placeholder="公司" class="company" :disabled="companies.length <= 1"
               data-test="company-select" @change="switchCompany">
      <el-option v-for="c in companies" :key="c.id" :label="`${c.companyName}（${c.companyCode}）`" :value="c.id" />
    </el-select>
    <el-tree-select v-model="model" :data="tree" :props="treeProps" node-key="id" check-strictly filterable clearable
                    default-expand-all :placeholder="placeholder ?? '选择部门'" class="tree" data-test="dept-select" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { orgApi } from '@/api'
import type { CompanyView, DeptNode } from '@/api/types'

/**
 * 公司 + 部门树选择。只列出管理范围涉及的公司与部门（后端已过滤，docs/05-ui.md §3.9）。
 * 祖先节点也会出现在树里（方便定位），选了范围外的部门，后端会返回 403。
 */
const model = defineModel<number | null>({ default: null })
const props = defineProps<{ placeholder?: string; initialCompanyId?: number | null }>()
const emit = defineEmits<{ (e: 'company', id: number | undefined): void }>()

const companies = ref<CompanyView[]>([])
const companyId = ref<number>()
const tree = ref<DeptNode[]>([])
const treeProps = { label: 'deptName', children: 'children', value: 'id' }

/** 换公司时清掉已选部门：旧公司的部门不在新树里，留着会把看不见的部门提交上去 */
async function switchCompany() {
  model.value = null
  await loadTree()
}

async function loadTree() {
  emit('company', companyId.value)
  tree.value = companyId.value ? await orgApi.tree(companyId.value) : []
}

onMounted(async () => {
  companies.value = await orgApi.companies()
  // 默认选业务公司（平台运维公司只有超级管理员一个人）
  const preferred = companies.value.find((c) => c.id === props.initialCompanyId)
    ?? companies.value.find((c) => c.companyCode !== 'PLATFORM') ?? companies.value[0]
  companyId.value = preferred?.id
  await loadTree()
})

defineExpose({ companyId })
</script>

<style scoped>
.dept-select {
  display: inline-flex;
  gap: 8px;
}
.company {
  width: 150px;
}
.tree {
  width: 230px;
}
</style>
