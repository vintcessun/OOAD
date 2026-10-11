<template>
  <div>
    <el-row :gutter="16">
      <el-col :span="9">
        <el-card>
          <el-select v-model="companyId" placeholder="公司" style="width: 100%" @change="loadTree" data-test="org-company">
            <el-option v-for="c in companies" :key="c.id" :label="`${c.companyName}（${c.companyCode}）`" :value="c.id" />
          </el-select>
          <el-input v-model="filter" placeholder="筛选部门" clearable class="gap" />
          <el-tree ref="treeRef" :data="tree" node-key="id" :props="{ label: 'deptName', children: 'children' }"
                   highlight-current :filter-node-method="filterNode" :default-expanded-keys="tree.map((d) => d.id)"
                   @node-click="selected = $event" data-test="org-tree" class="tree" />
          <el-empty v-if="!tree.length" description="管理范围内没有部门，或尚未导入组织数据" />
        </el-card>
      </el-col>
      <el-col :span="15">
        <el-card class="page-card">
          <template v-if="selected">
            <h3>{{ selected.deptName }}</h3>
            <el-descriptions :column="2" border>
              <el-descriptions-item label="层级">{{ selected.level }}</el-descriptions-item>
              <el-descriptions-item label="下级部门">{{ selected.children.length }}</el-descriptions-item>
              <el-descriptions-item label="负责人（部门表登记）">{{ selected.managerName ?? '—' }}</el-descriptions-item>
              <el-descriptions-item label="已关联用户">{{ selected.managerUserId ?? '未唯一确定' }}</el-descriptions-item>
            </el-descriptions>
            <p class="muted">负责人只是组织信息，不带来任何管理权限；管理权限只来自角色指派。</p>
            <div class="toolbar">
              <el-button v-if="auth.has('rbac:user:view')"
                         @click="router.push({ path: '/users' })">查看用户 →</el-button>
              <el-button v-perm="'rbac:assignment:assign'" type="primary" @click="openBatch(false)" data-test="org-batch">
                对本部门批量授权
              </el-button>
              <el-button v-perm="'rbac:assignment:revoke'" @click="openBatch(true)">对本部门批量回收</el-button>
            </div>
          </template>
          <el-empty v-else description="在左侧选择部门" />
        </el-card>
        <el-card>
          <div class="toolbar">
            <el-button v-perm.hide="'rbac:org:import'" @click="orgImportVisible = true" data-test="open-import-org">导入组织与员工</el-button>
            <el-button v-perm.hide="'rbac:permission:create'" @click="permImportVisible = true" data-test="open-import-perm">
              导入功能权限清单
            </el-button>
          </div>
          <el-alert type="info" :closable="false" show-icon
                    title="导入不会分配任何角色。导入后请审核异常清单，再用批量授权人工分配。" />
        </el-card>
      </el-col>
    </el-row>

    <BatchAssignDialog v-if="batchVisible" v-model="batchVisible" :revoke="batchRevoke" initial-target-type="DEPARTMENT"
                       :initial-dept-id="selected?.id" :initial-company-id="companyId" />

    <el-dialog v-model="orgImportVisible" title="导入部门信息表与员工信息表" width="640px" data-test="import-org-dialog">
      <el-form label-width="110px">
        <el-form-item label="公司编码"><el-input v-model="orgForm.companyCode" placeholder="如 SZ" data-test="import-code" /></el-form-item>
        <el-form-item label="公司名称"><el-input v-model="orgForm.companyName" data-test="import-name" /></el-form-item>
        <el-form-item label="部门信息表">
          <input type="file" accept=".xlsx" data-test="import-dept-file" @change="pick($event, 'dept')" />
        </el-form-item>
        <el-form-item label="员工信息表">
          <input type="file" accept=".xlsx" data-test="import-emp-file" @change="pick($event, 'emp')" />
        </el-form-item>
      </el-form>
      <ImportReportView v-if="orgReport" :report="orgReport" />
      <template #footer>
        <el-button @click="orgImportVisible = false">关闭</el-button>
        <el-button type="primary" :loading="importing" :disabled="!files.dept || !files.emp || !orgForm.companyCode"
                   @click="importOrg" data-test="import-org-submit">导入</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="permImportVisible" title="导入应用软件功能权限清单" width="640px" data-test="import-perm-dialog">
      <input type="file" accept=".xlsx" data-test="import-perm-file" @change="pick($event, 'perm')" />
      <ImportReportView v-if="permReport" :report="permReport" />
      <template #footer>
        <el-button @click="permImportVisible = false">关闭</el-button>
        <el-button type="primary" :loading="importing" :disabled="!files.perm" @click="importPerm"
                   data-test="import-perm-submit">导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { TreeInstance } from 'element-plus'
import { orgApi } from '@/api'
import type { CompanyView, DeptNode, ImportReport } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import BatchAssignDialog from '@/components/BatchAssignDialog.vue'
import ImportReportView from '@/components/ImportReportView.vue'

/** 组织管理（docs/05-ui.md §3.9）：部门树、按部门批量授权、导入三份 Excel。 */
const auth = useAuthStore()
const router = useRouter()
const companies = ref<CompanyView[]>([])
const companyId = ref<number>()
const tree = ref<DeptNode[]>([])
const treeRef = ref<TreeInstance>()
const filter = ref('')
const selected = ref<DeptNode | null>(null)
const batchVisible = ref(false)
const batchRevoke = ref(false)
const orgImportVisible = ref(false)
const permImportVisible = ref(false)
const importing = ref(false)
const orgForm = reactive({ companyCode: 'SZ', companyName: '市政公司' })
const files = reactive<{ dept: File | null; emp: File | null; perm: File | null }>({ dept: null, emp: null, perm: null })
const orgReport = ref<ImportReport | null>(null)
const permReport = ref<ImportReport | null>(null)

watch(filter, (v) => treeRef.value?.filter(v))
const filterNode = (value: string, data: Record<string, unknown>) => !value || String(data.deptName).includes(value)

async function loadCompanies() {
  companies.value = await orgApi.companies()
  if (!companies.value.some((c) => c.id === companyId.value)) {
    companyId.value = companies.value.find((c) => c.companyCode !== 'PLATFORM')?.id ?? companies.value[0]?.id
  }
  await loadTree()
}

async function loadTree() {
  selected.value = null
  tree.value = companyId.value ? await orgApi.tree(companyId.value) : []
}

function openBatch(revoke: boolean) {
  batchRevoke.value = revoke
  batchVisible.value = true
}

function pick(e: Event, kind: 'dept' | 'emp' | 'perm') {
  files[kind] = (e.target as HTMLInputElement).files?.[0] ?? null
}

async function importOrg() {
  importing.value = true
  try {
    orgReport.value = await orgApi.importOrganization(orgForm.companyCode, orgForm.companyName, files.dept!, files.emp!)
    ElMessage.success('导入完成，请核对异常清单')
    companies.value = await orgApi.companies()
    // 切到刚导入的公司
    companyId.value = companies.value.find((c) => c.companyCode === orgForm.companyCode)?.id ?? companyId.value
    await loadTree()
  } finally {
    importing.value = false
  }
}

async function importPerm() {
  importing.value = true
  try {
    permReport.value = await orgApi.importPermissions(files.perm!)
    ElMessage.success('导入完成，请核对异常清单')
  } finally {
    importing.value = false
  }
}

onMounted(loadCompanies)
</script>

<style scoped>
.gap {
  margin: 8px 0;
}
.tree {
  max-height: 560px;
  overflow: auto;
}
</style>
