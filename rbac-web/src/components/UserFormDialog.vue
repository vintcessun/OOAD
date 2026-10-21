<template>
  <el-dialog v-model="visible" :title="user ? '编辑用户' : '新增用户'" width="560px" destroy-on-close data-test="user-form">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
      <el-form-item label="用户名" prop="username">
        <el-input v-model="form.username" :disabled="!!user" data-test="form-username" />
      </el-form-item>
      <el-form-item label="姓名" prop="realName">
        <el-input v-model="form.realName" data-test="form-realname" />
      </el-form-item>
      <el-form-item label="部门" prop="departmentId">
        <DeptTreeSelect v-model="form.departmentId" :initial-company-id="user?.companyId" />
      </el-form-item>
      <el-form-item label="类型">
        <el-radio-group v-model="form.userType" :disabled="!!user" data-test="form-type">
          <el-radio value="EMPLOYEE">正式员工</el-radio>
          <el-radio value="TEMPORARY">临时用户</el-radio>
          <el-radio value="SYSTEM">系统账号</el-radio>
        </el-radio-group>
      </el-form-item>
      <!-- 临时用户没有工号、电话等员工信息 -->
      <el-form-item v-if="form.userType === 'EMPLOYEE' && !user" label="工号" prop="employeeNo">
        <el-input v-model="form.employeeNo" data-test="form-employee-no" />
      </el-form-item>
      <el-form-item v-if="form.userType !== 'TEMPORARY'" label="电话">
        <el-input v-model="form.phone" />
      </el-form-item>
      <el-form-item label="岗位">
        <el-input v-model="form.position" />
      </el-form-item>
      <el-form-item v-if="!user" label="初始口令">
        <el-input v-model="form.password" type="password" show-password placeholder="留空则使用初始口令，首次登录须修改" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="loading" @click="submit" data-test="form-submit">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { userApi } from '@/api'
import type { User, UserType } from '@/api/types'
import DeptTreeSelect from './DeptTreeSelect.vue'

/** 新增 / 编辑用户（docs/05-ui.md §3.2）。规则与后端一致：临时用户无工号电话，用完由管理员停用；正式员工必须有工号。 */
const visible = defineModel<boolean>({ default: false })
const props = defineProps<{ user: User | null }>()
const emit = defineEmits<{ (e: 'done'): void }>()

const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({
  username: props.user?.username ?? '',
  realName: props.user?.realName ?? '',
  departmentId: props.user?.departmentId ?? null as number | null,
  userType: (props.user?.userType ?? 'EMPLOYEE') as UserType,
  employeeNo: '',
  phone: props.user?.phone ?? '',
  position: props.user?.position ?? '',
  password: '',
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名' }],
  realName: [{ required: true, message: '请输入姓名' }],
  departmentId: [{ required: true, message: '请选择部门' }],
  employeeNo: [{ validator: (_r, v, cb) => (form.userType === 'EMPLOYEE' && !props.user && !v ? cb(new Error('正式员工必须填写工号')) : cb()) }],
}

async function submit() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  loading.value = true
  try {
    if (props.user) {
      await userApi.update(props.user.id, {
        realName: form.realName,
        departmentId: form.departmentId,
        phone: form.userType === 'TEMPORARY' ? undefined : form.phone,
        position: form.position,
      })
    } else {
      const temp = form.userType === 'TEMPORARY'
      await userApi.create({
        username: form.username,
        realName: form.realName,
        departmentId: form.departmentId,
        userType: form.userType,
        employeeNo: temp ? null : form.employeeNo || null,
        phone: temp ? null : form.phone || null,
        position: form.position || null,
        password: form.password || null,
      })
    }
    ElMessage.success('已保存')
    visible.value = false
    emit('done')
  } finally {
    loading.value = false
  }
}
</script>
