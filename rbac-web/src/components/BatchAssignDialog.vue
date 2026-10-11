<template>
  <el-dialog v-model="visible" :title="revoke ? '批量回收' : '批量授权'" width="640px" destroy-on-close data-test="batch-dialog">
    <el-form label-width="90px">
      <el-form-item label="目标">
        <el-radio-group v-model="form.targetType" data-test="batch-target-type">
          <el-radio value="COMPANY">分公司 / 公司</el-radio>
          <el-radio value="DEPARTMENT">部门</el-radio>
          <el-radio value="USER" :disabled="!userIds?.length">员工（列表中勾选的 {{ userIds?.length ?? 0 }} 人）</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="form.targetType === 'COMPANY'" label="公司">
        <el-select v-model="companyId" placeholder="选择公司" data-test="batch-company">
          <el-option v-for="c in companies" :key="c.id" :label="`${c.companyName}（${c.companyCode}）`" :value="c.id" />
        </el-select>
        <span class="muted hint">要按分公司授权，请选「部门」并选中分公司节点、勾选含下级</span>
      </el-form-item>
      <el-form-item v-if="form.targetType === 'DEPARTMENT'" label="部门">
        <DeptTreeSelect v-model="deptId" />
        <el-checkbox v-model="form.includeDescendants" class="hint">含下级部门</el-checkbox>
      </el-form-item>
      <el-form-item label="角色">
        <el-select v-model="form.roleId" filterable placeholder="选择角色" data-test="batch-role">
          <el-option v-for="r in roles" :key="r.id" :label="`${r.roleName}（${r.roleCode}）`" :value="r.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="适用部门">
        <el-radio-group v-model="scopeMode">
          <el-radio value="own">{{ revoke ? '该角色的全部指派' : '各自所属部门' }}</el-radio>
          <el-radio value="specified">指定部门</el-radio>
        </el-radio-group>
        <DeptTreeSelect v-if="scopeMode === 'specified'" v-model="scopeDeptId" class="hint" />
      </el-form-item>
      <el-form-item label="原因">
        <el-input v-model="form.reason" maxlength="200" data-test="batch-reason" />
      </el-form-item>
      <el-form-item label="执行方式">
        <el-radio-group v-model="form.mode" data-test="batch-mode">
          <el-radio value="ATOMIC">整批原子（有一人不通过就全部不执行）</el-radio>
          <el-radio value="PARTIAL">部分执行（执行通过的，列出失败的）</el-radio>
        </el-radio-group>
      </el-form-item>
    </el-form>
    <BatchResultView v-if="result" :result="result" />
    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
      <el-button v-if="result && !result.committed" type="warning" @click="retryPartial" data-test="batch-retry-partial">
        改为部分执行再提交
      </el-button>
      <el-button type="primary" :loading="loading" :disabled="!canSubmit" @click="submit" data-test="batch-submit">提交</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { assignmentApi, orgApi, roleApi } from '@/api'
import type { BatchAssign, BatchMode, BatchResult, CompanyView, Role, TargetType } from '@/api/types'
import DeptTreeSelect from './DeptTreeSelect.vue'
import BatchResultView from './BatchResultView.vue'

/**
 * 按分公司 / 部门 / 员工批量授权与回收（docs/05-ui.md §3.2）。用户列表与组织管理共用。
 * 默认整批原子：未通过校验时明确告诉用户「一个都没写入」，并提供改为部分执行的按钮。
 */
const visible = defineModel<boolean>({ default: false })
const props = defineProps<{
  revoke?: boolean
  userIds?: number[]
  initialTargetType?: TargetType
  initialDeptId?: number | null
  initialCompanyId?: number | null
}>()
const emit = defineEmits<{ (e: 'done', r: BatchResult): void }>()

const roles = ref<Role[]>([])
const companies = ref<CompanyView[]>([])
const companyId = ref<number | undefined>(props.initialCompanyId ?? undefined)
const deptId = ref<number | null>(props.initialDeptId ?? null)
const scopeMode = ref<'own' | 'specified'>('own')
const scopeDeptId = ref<number | null>(null)
const loading = ref(false)
const result = ref<BatchResult | null>(null)
const form = reactive({
  targetType: (props.initialTargetType ?? (props.userIds?.length ? 'USER' : 'DEPARTMENT')) as TargetType,
  includeDescendants: true,
  roleId: undefined as number | undefined,
  reason: '',
  mode: 'ATOMIC' as BatchMode,
})

const targetIds = computed<number[]>(() => {
  if (form.targetType === 'USER') return props.userIds ?? []
  if (form.targetType === 'COMPANY') return companyId.value ? [companyId.value] : []
  return deptId.value ? [deptId.value] : []
})
const canSubmit = computed(() => form.roleId !== undefined && targetIds.value.length > 0
  && (scopeMode.value === 'own' || scopeDeptId.value !== null))

onMounted(async () => {
  ;[roles.value, companies.value] = await Promise.all([roleApi.list(undefined, 'ENABLED'), orgApi.companies()])
  if (companyId.value === undefined) {
    companyId.value = companies.value.find((c) => c.companyCode !== 'PLATFORM')?.id ?? companies.value[0]?.id
  }
})

function buildRequest(): BatchAssign {
  return {
    targetType: form.targetType,
    targetIds: targetIds.value,
    includeDescendants: form.includeDescendants,
    roleId: form.roleId!,
    scopeDepartmentId: scopeMode.value === 'specified' ? scopeDeptId.value : null,
    reason: form.reason || undefined,
    mode: form.mode,
  }
}

async function submit() {
  loading.value = true
  try {
    const req = buildRequest()
    result.value = props.revoke ? await assignmentApi.batchRevoke(req) : await assignmentApi.batch(req)
    emit('done', result.value)
  } finally {
    loading.value = false
  }
}

async function retryPartial() {
  form.mode = 'PARTIAL'
  await submit()
}
</script>

<style scoped>
.hint {
  margin-left: 8px;
}
</style>
