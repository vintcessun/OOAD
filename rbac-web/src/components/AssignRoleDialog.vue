<template>
  <el-dialog v-model="visible" title="分配角色" width="560px" destroy-on-close data-test="assign-dialog">
    <el-form label-width="90px">
      <el-form-item label="角色">
        <el-select v-model="roleId" filterable placeholder="只列出启用的角色" data-test="assign-role">
          <el-option v-for="r in roles" :key="r.id" :label="`${r.roleName}（${r.roleCode}）`" :value="r.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="适用部门">
        <el-radio-group v-model="scopeMode">
          <el-radio value="own">所属部门</el-radio>
          <el-radio value="specified">指定部门（含下级）</el-radio>
        </el-radio-group>
        <DeptTreeSelect v-if="scopeMode === 'specified'" v-model="scopeDeptId" :initial-company-id="companyId" />
      </el-form-item>
      <el-form-item label="原因">
        <el-input v-model="reason" maxlength="200" />
      </el-form-item>
    </el-form>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="loading" :disabled="!roleId || (scopeMode === 'specified' && !scopeDeptId)" @click="submit" data-test="assign-submit">分配</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { roleApi, userApi } from '@/api'
import type { ApiError } from '@/api/http'
import type { Role } from '@/api/types'
import DeptTreeSelect from './DeptTreeSelect.vue'

const visible = defineModel<boolean>({ default: false })
const props = defineProps<{ userId: number; companyId?: number | null }>()
const emit = defineEmits<{ (e: 'done'): void }>()

const roles = ref<Role[]>([])
const roleId = ref<number>()
const scopeMode = ref<'own' | 'specified'>('own')
const scopeDeptId = ref<number | null>(null)
const reason = ref('')
const loading = ref(false)
const error = ref('')

/** 后端拒绝时按错误码给出说明（docs/05-ui.md §3.3）。 */
const HINTS: Record<number, string> = {
  40302: '用户或适用部门不在你的管理范围内',
  40303: '该角色含有你在适用部门不具备的权限，不能授出',
  40901: '该用户在该部门已持有此角色',
}

onMounted(async () => {
  roles.value = await roleApi.list(undefined, 'ENABLED')
})

async function submit() {
  loading.value = true
  error.value = ''
  try {
    await userApi.assign(props.userId, roleId.value!, scopeMode.value === 'specified' ? scopeDeptId.value : null,
      reason.value || undefined)
    visible.value = false
    emit('done')
  } catch (e) {
    const err = e as ApiError
    error.value = HINTS[err.code] ?? err.message
  } finally {
    loading.value = false
  }
}
</script>
