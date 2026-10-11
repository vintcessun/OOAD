<template>
  <el-card>
    <div class="toolbar">
      <el-select v-model="query.eventType" placeholder="事件" clearable filterable style="width: 190px" data-test="audit-event">
        <el-option-group v-for="g in EVENT_GROUPS" :key="g.label" :label="g.label">
          <el-option v-for="e in g.events" :key="e" :label="e" :value="e" />
        </el-option-group>
      </el-select>
      <el-input v-model="query.actor" placeholder="谁（用户名）" clearable style="width: 140px" />
      <el-input v-model="query.target" placeholder="对谁" clearable style="width: 140px" />
      <el-select v-model="query.result" placeholder="结果" clearable style="width: 110px">
        <el-option v-for="r in ['SUCCESS', 'DENIED', 'FAILED']" :key="r" :label="r" :value="r" />
      </el-select>
      <el-date-picker v-model="range" type="datetimerange" value-format="YYYY-MM-DDTHH:mm:ss"
                      start-placeholder="从" end-placeholder="到" />
      <el-button type="primary" @click="search" data-test="audit-search">查询</el-button>
    </div>
    <el-table v-loading="loading" :data="page.items" data-test="audit-table">
      <el-table-column type="expand">
        <template #default="{ row }">
          <el-descriptions :column="2" size="small" border class="expand">
            <el-descriptions-item label="明细" :span="2">{{ row.detail ?? '—' }}</el-descriptions-item>
            <el-descriptions-item label="权限码">{{ row.permissionCode ?? '—' }}</el-descriptions-item>
            <el-descriptions-item label="对象 id">{{ row.targetId ?? '—' }}</el-descriptions-item>
            <el-descriptions-item label="判定耗时">{{ row.latencyUs === null ? '—' : row.latencyUs + ' µs' }}</el-descriptions-item>
            <el-descriptions-item label="命中缓存">{{ row.cacheHit === null ? '—' : row.cacheHit ? '是' : '否' }}</el-descriptions-item>
          </el-descriptions>
        </template>
      </el-table-column>
      <el-table-column label="什么时候" width="175">
        <template #default="{ row }">{{ row.occurredAt.replace('T', ' ').slice(0, 23) }}</template>
      </el-table-column>
      <el-table-column label="谁" width="110">
        <template #default="{ row }">{{ row.actorName ?? '—' }}</template>
      </el-table-column>
      <el-table-column label="在哪里" width="130">
        <template #default="{ row }">{{ row.clientIp ?? '—' }}</template>
      </el-table-column>
      <el-table-column label="做了什么" min-width="200">
        <template #default="{ row }">
          <span class="mono">{{ row.eventType }}</span>
          <div class="muted">{{ row.permissionCode ?? row.detail ?? '' }}</div>
        </template>
      </el-table-column>
      <el-table-column label="对谁" min-width="150">
        <template #default="{ row }">
          <el-link v-if="row.targetType === 'USER' && row.targetId" type="primary" @click="router.push(`/users/${row.targetId}`)">
            {{ row.targetName ?? row.targetId }}
          </el-link>
          <el-link v-else-if="row.targetType === 'ROLE' && row.targetId" type="primary" @click="router.push(`/roles/${row.targetId}`)">
            {{ row.targetName ?? row.targetId }}
          </el-link>
          <span v-else>{{ row.targetType ?? '' }} {{ row.targetName ?? row.targetId ?? '' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="结果" width="95">
        <template #default="{ row }">
          <el-tag size="small" :type="row.result === 'SUCCESS' ? 'success' : row.result === 'DENIED' ? 'danger' : 'warning'">
            {{ row.result }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="为什么" min-width="170">
        <template #default="{ row }"><span class="mono">{{ row.reason ?? '' }}</span></template>
      </el-table-column>
    </el-table>
    <div class="pager">
      <el-pagination v-model:current-page="pageNo" v-model:page-size="size" :total="page.total" :page-sizes="[20, 50, 100]"
                     layout="total, sizes, prev, pager, next" @change="load" />
    </div>
    <p class="muted">审计日志只插入、只读，没有修改和删除。批量操作以一条对象类型为 BATCH 的记录出现。</p>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { statsApi } from '@/api'
import type { AuditLog, Page } from '@/api/types'

/** 审计日志（docs/05-ui.md §3.8）：谁、什么时候、在哪里、对谁、做了什么、结果如何、为什么。 */
const route = useRoute()
const router = useRouter()

const EVENT_GROUPS = [
  { label: '登录', events: ['LOGIN', 'LOGOUT', 'PASSWORD_CHANGE'] },
  { label: '鉴权', events: ['AUTH_SUCCESS', 'AUTH_FAILED', 'AUTHZ_QUERY'] },
  { label: '授权', events: ['ROLE_ASSIGN', 'ROLE_REVOKE', 'PERMISSION_ASSIGN', 'PERMISSION_REVOKE'] },
  { label: '用户', events: ['USER_CREATE', 'USER_UPDATE', 'USER_STATUS', 'USER_DELETE'] },
  { label: '角色', events: ['ROLE_CREATE', 'ROLE_UPDATE', 'ROLE_DELETE'] },
  { label: '菜单与资源', events: ['MENU_CREATE', 'MENU_UPDATE', 'MENU_DELETE', 'RESOURCE_CREATE', 'PERMISSION_CREATE'] },
  { label: '导入', events: ['IMPORT_ORG', 'IMPORT_PERMISSIONS'] },
]

const q = route.query
const query = reactive({ eventType: (q.eventType as string) ?? '', actor: (q.actor as string) ?? '',
  target: (q.target as string) ?? '', result: (q.result as string) ?? '' })
const range = ref<[string, string] | undefined>(q.from ? [q.from as string, (q.to as string) ?? ''] : undefined)
const pageNo = ref(1)
const size = ref(20)
const page = ref<Page<AuditLog>>({ items: [], total: 0, page: 0, size: 20 })
const loading = ref(false)

let seq = 0

async function load() {
  const mine = ++seq
  loading.value = true
  try {
    const result = await statsApi.auditLogs({ ...query, from: range.value?.[0] || undefined, to: range.value?.[1] || undefined,
      page: pageNo.value - 1, size: size.value })
    if (mine === seq) page.value = result
  } finally {
    if (mine === seq) loading.value = false
  }
}

function search() {
  pageNo.value = 1
  load()
}

onMounted(load)
</script>

<style scoped>
.expand {
  margin: 0 48px;
}
</style>
