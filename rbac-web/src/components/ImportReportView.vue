<template>
  <div class="report" data-test="import-report">
    <h4>导入结果</h4>
    <div class="counts">
      <div v-for="(v, k) in report.counts" :key="k" class="count">
        <div class="muted">{{ countLabel(String(k)) }}</div>
        <div class="num" :data-test="'count-' + k">{{ v }}</div>
      </div>
    </div>
    <ul v-if="report.notes.length" class="notes">
      <li v-for="n in report.notes" :key="n">{{ n }}</li>
    </ul>
    <h4>异常清单（{{ report.anomalies.length }}）<span class="muted">——这些行没有写入，需要人工处理</span></h4>
    <el-table v-if="report.anomalies.length" :data="report.anomalies" size="small" max-height="240" data-test="anomalies">
      <el-table-column prop="sheet" label="表" width="110" />
      <el-table-column prop="row" label="行" width="60" />
      <el-table-column prop="reason" label="原因" />
      <el-table-column prop="value" label="值" />
    </el-table>
  </div>
</template>

<script setup lang="ts">
import type { ImportReport } from '@/api/types'

defineProps<{ report: ImportReport }>()

/** rolePermissions.SYS_ADMIN 这类按角色细分的计数显示为「角色权限 · SYS_ADMIN」 */
function countLabel(k: string): string {
  if (k.startsWith('rolePermissions.')) return '角色权限 · ' + k.slice('rolePermissions.'.length)
  return COUNT_LABEL[k] ?? k
}

const COUNT_LABEL: Record<string, string> = {
  departmentsInSheet: '表中部门',
  departmentsCreated: '新建部门',
  employeesInSheet: '表中员工',
  usersCreated: '新建用户',
  usersAlreadyPresent: '已存在跳过',
  managersLinked: '关联负责人',
  systems: '业务系统',
  modules: '功能模块',
  resources: '资源',
  permissions: '权限',
  rolePermissions: '角色权限',
  rolePermissionsSelfOnlyChanged: '「只限本人」已更新',
  rolePermissionsRevoked: '按清单回收',
}
</script>

<style scoped>
.report {
  margin-top: 12px;
}
.counts {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
}
.count {
  background: #f5f7fa;
  border-radius: 4px;
  padding: 6px 10px;
}
.num {
  font-size: 18px;
  font-weight: 600;
}
.notes {
  color: #606266;
  font-size: 13px;
}
</style>
