<template>
  <div>
    <el-card class="page-card">
      <el-form label-width="90px" @submit.prevent="explain">
        <el-form-item label="用户">
          <el-select v-model="userId" filterable remote :remote-method="searchUsers" placeholder="默认本人；输入用户名或姓名查找"
                     clearable style="width: 340px" data-test="explain-user">
            <el-option v-for="u in userOptions" :key="u.id" :label="`${u.realName}（${u.username}）`" :value="u.id" />
          </el-select>
          <span v-if="!canPickOthers" class="muted hint">你只能解释自己的权限（查别人需要 rbac:user:view 与 rbac:authz:explain）</span>
        </el-form-item>
        <el-form-item label="权限">
          <el-input v-model="permission" placeholder="权限码，如 oa:m01:f02:approve" style="width: 340px" class="mono"
                    data-test="explain-permission" />
        </el-form-item>
        <el-form-item label="目标部门">
          <DeptTreeSelect v-if="canPickDept" v-model="targetDeptId" placeholder="可空：只问能否执行该操作" />
          <el-input-number v-else v-model="targetDeptInput" :controls="false" placeholder="部门 id（可空）" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" native-type="submit" :disabled="!permission" :loading="loading" data-test="explain-submit">
            解释
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card v-if="decision" data-test="explain-result">
      <div class="verdict">
        <span :class="decision.allowed ? 'allow' : 'deny'" class="big" data-test="explain-verdict">
          {{ decision.allowed ? '✓ ALLOW' : '✗ DENY' }}
        </span>
        <span data-test="explain-subject">{{ decision.username ?? '（不存在的用户）' }}</span>
        <span class="mono">{{ decision.reason }}</span>
        <span class="muted">{{ label(REASON_LABEL, decision.reason) }}</span>
        <span class="spacer" />
        <span class="muted">耗时 {{ (decision.latencyUs / 1000).toFixed(2) }} ms · {{ decision.cacheHit ? '缓存命中' : '查库' }}</span>
      </div>
      <h4>判定依据</h4>
      <ol data-test="explain-trace">
        <li v-for="(t, i) in decision.trace ?? []" :key="i">{{ t }}</li>
      </ol>
      <p v-if="decision.matched">
        依据：<b>{{ roleAtScope(decision.matched.roleName, decision.matched.scopeDeptName) }}</b>
      </p>
      <el-alert v-if="decision.selfOnly" type="warning" :closable="false" show-icon
                title="该权限只限本人记录，由业务系统按记录归属过滤" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { authzApi, userApi } from '@/api'
import type { ApiError } from '@/api/http'
import type { Decision, User } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { REASON_LABEL, label, roleAtScope } from '@/utils/format'
import DeptTreeSelect from '@/components/DeptTreeSelect.vue'

/**
 * 权限解释（docs/05-ui.md §3.10）。与鉴权接口共用同一个判定过程，只多返回 trace；前端不做任何推断。
 * 查别人时后端先检查调用者的范围，不满足返回 403。
 */
const auth = useAuthStore()
const route = useRoute()
const userId = ref<number>()
const userOptions = ref<User[]>([])
const permission = ref((route.query.permission as string) ?? '')
const targetDeptId = ref<number | null>(null)
const targetDeptInput = ref<number>()
const decision = ref<Decision | null>(null)
const loading = ref(false)

const canPickOthers = computed(() => auth.has('rbac:user:view') && auth.has('rbac:authz:explain'))
const canPickDept = computed(() => auth.has('rbac:org:view'))

async function searchUsers(keyword: string) {
  if (!canPickOthers.value) return
  userOptions.value = (await userApi.list({ keyword, size: 20 })).items
}

async function explain() {
  loading.value = true
  try {
    decision.value = await authzApi.explain({ userId: userId.value ?? auth.me?.user.id, permission: permission.value.trim(),
      targetDeptId: canPickDept.value ? targetDeptId.value : targetDeptInput.value ?? null })
  } catch (e) {
    decision.value = null
    if ((e as ApiError).status === 403) {
      // 统一错误处理已提示「无权查询其他用户的权限」
    }
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  if (auth.me) {
    userOptions.value = [auth.me.user]
  }
  const fromQuery = Number(route.query.userId)
  if (fromQuery && canPickOthers.value) {
    userId.value = fromQuery // 先定下要解释的用户，再补上显示名
    const d = await userApi.detail(fromQuery)
    userOptions.value = [d.user]
  }
})
</script>

<style scoped>
.verdict {
  display: flex;
  align-items: center;
  gap: 12px;
}
.big {
  font-size: 22px;
  font-weight: 700;
}
.hint {
  margin-left: 8px;
}
.spacer {
  flex: 1;
}
</style>
