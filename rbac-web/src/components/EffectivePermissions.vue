<script setup lang="ts">
import { computed, ref } from 'vue'
import { NTag } from 'naive-ui'
import type { EffectiveVO, PermissionVO } from '@/api/types'
import ReadState from './ReadState.vue'
import TechText from './TechText.vue'
const props = defineProps<{ value: EffectiveVO; showRoles?: boolean }>()
const filter = ref('')
const results = computed(() => props.value.permissions.filter(p => [p.code, p.name].some(value => value.toLowerCase().includes(filter.value.trim().toLowerCase()))))
function permissionKey(kind: PermissionVO['kind'], code: string) { return `${kind}:${code}` }
const sources = computed(() => {
  const byPermission = new Map<string, EffectiveVO['sources']>()
  for (const source of props.value.sources) {
    const key = permissionKey(source.permissionKind, source.permissionCode)
    const entries = byPermission.get(key) ?? []
    entries.push(source)
    byPermission.set(key, entries)
  }
  return byPermission
})
const roleNames = computed(() => new Map([...props.value.directRoles, ...props.value.effectiveRoles].map(role => [role.code, role.name])))
</script>
<template>
  <section class="effective">
    <div v-if="showRoles !== false" class="effective-roles">
      <div><h3>直接角色</h3><span v-if="!value.directRoles.length" class="muted">无</span><NTag v-for="role in value.directRoles" :key="role.id" size="small">{{ role.name }}</NTag></div>
      <div><h3>有效角色</h3><span v-if="!value.effectiveRoles.length" class="muted">无</span><NTag v-for="role in value.effectiveRoles" :key="role.id" size="small">{{ role.name }}</NTag></div>
    </div>
    <div class="section-heading"><h3>权限及来源</h3><input v-if="value.permissions.length" v-model="filter" aria-label="筛选有效权限" placeholder="搜索权限名称或编码" /></div>
    <ReadState v-if="!results.length" empty :description="value.permissions.length ? '无匹配权限' : '暂无有效权限'" />
    <div v-else class="table-scroll" tabindex="0" aria-label="有效权限及来源">
      <table class="data-table"><thead><tr><th>权限</th><th>种类</th><th>来源与路径</th></tr></thead>
        <tbody><tr v-for="permission in results" :key="permission.id">
          <td><strong>{{ permission.name }}</strong><TechText :value="permission.code" /></td>
          <td><NTag size="small" :type="permission.kind === 'UI' ? 'info' : 'default'">{{ permission.kind }}</NTag></td>
          <td>
            <span v-if="!sources.get(permissionKey(permission.kind, permission.code))?.length" class="muted">—</span>
            <ul class="source-list">
              <li v-for="(source, index) in sources.get(permissionKey(permission.kind, permission.code))" :key="index">
                <NTag size="small" :type="source.inherited ? 'info' : 'success'">{{ source.inherited ? '继承' : '直接' }}</NTag>
                <span :title="source.path.join(' → ')">{{ source.path.length ? source.path.map(code => roleNames.get(code) ?? code).join(' → ') : roleNames.get(source.roleCode) ?? source.roleCode }}</span>
              </li>
            </ul>
          </td>
        </tr></tbody>
      </table>
    </div>
  </section>
</template>
