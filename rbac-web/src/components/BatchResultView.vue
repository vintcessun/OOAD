<template>
  <div class="batch-result" data-test="batch-result">
    <el-alert v-if="!result.committed" type="error" :closable="false" show-icon
              :title="`未执行：${result.failed} 人未通过校验（整批原子模式），这一批一个都没有写入`" />
    <el-alert v-else-if="result.failed > 0" type="warning" :closable="false" show-icon
              :title="`部分执行：成功 ${result.succeeded} 人，失败 ${result.failed} 人`" />
    <el-alert v-else type="success" :closable="false" show-icon :title="`已执行：成功 ${result.succeeded} 人`" />
    <p class="summary" data-test="batch-summary">
      请求 {{ result.requested }} 人 · 成功 {{ result.succeeded }} · 已持有 / 跳过 {{ result.duplicated }} · 失败 {{ result.failed }}
    </p>
    <div v-if="result.failures.length" class="failures">
      <table>
        <thead><tr><th>用户 id</th><th>原因码</th><th>说明</th></tr></thead>
        <tbody>
          <tr v-for="f in result.failures" :key="f.userId + f.reason">
            <td>{{ f.userId }}</td>
            <td class="mono">{{ f.reason }}</td>
            <td>{{ label(FAILURE_LABEL, f.reason) }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { BatchResult } from '@/api/types'
import { FAILURE_LABEL, label } from '@/utils/format'

// 永远显示 committed、成功、已持有、失败与失败清单，不只弹一个「操作成功」（docs/05-ui.md §6）
defineProps<{ result: BatchResult }>()
</script>

<style scoped>
.summary {
  margin: 8px 0;
}
.failures {
  max-height: 240px;
  overflow: auto;
}
.failures table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.failures th,
.failures td {
  text-align: left;
  padding: 6px 8px;
  border-bottom: 1px solid #ebeef5;
}
</style>
