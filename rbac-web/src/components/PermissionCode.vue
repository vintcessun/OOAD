<template>
  <span class="mono code" :title="code">
    <template v-for="(seg, i) in segments" :key="i">
      <span v-if="i > 0" class="sep">:</span><span :class="'seg seg' + Math.min(i, 3)">{{ seg }}</span>
    </template>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { splitPermissionCode } from '@/utils/format'

// 权限码用等宽字体，系统 / 模块 / 功能点 / 操作四段用不同灰度区分（docs/05-ui.md §6）
const props = defineProps<{ code: string }>()
const segments = computed(() => splitPermissionCode(props.code))
</script>

<style scoped>
.code {
  font-size: 13px;
}
.sep {
  color: #c0c4cc;
}
.seg0 {
  color: #303133;
  font-weight: 600;
}
.seg1 {
  color: #606266;
}
.seg2 {
  color: #909399;
}
.seg3 {
  color: #409eff;
  font-weight: 600;
}
</style>
