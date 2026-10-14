<template>
  <div>
    <el-row :gutter="16">
      <el-col v-for="m in metrics" :key="m.key" :span="6" class="col">
        <el-card shadow="hover" :class="['metric', { clickable: m.key === 'abnormalCount' }]" :data-test="'metric-' + m.key"
                 @click="m.key === 'abnormalCount' && openAbnormal()">
          <div class="title">{{ m.title }}</div>
          <div class="value" :class="{ warn: m.key === 'abnormalCount' && (data?.abnormalCount ?? 0) > 0 }">
            {{ data ? m.format(data) : '—' }}
          </div>
          <div class="muted">{{ m.hint }}</div>
        </el-card>
      </el-col>
    </el-row>
    <p class="muted">统计时间 {{ formatTime(data?.generatedAt) }} · 每 10 秒刷新，页面不可见时暂停 · 口径见 docs/05-ui.md §3.1.1，全部来自审计日志中的真实事件</p>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { statsApi } from '@/api'
import type { Dashboard } from '@/api/types'
import { formatTime, percent, toLocalDateTime } from '@/utils/format'

const router = useRouter()
const data = ref<Dashboard | null>(null)
let timer: ReturnType<typeof setInterval> | undefined

/** 十一项指标（需求「前端功能 › Dashboard」）。 */
const metrics: { key: keyof Dashboard; title: string; hint: string; format: (d: Dashboard) => string }[] = [
  { key: 'userCount', title: '用户数量', hint: '未删除，含停用与临时', format: (d) => d.userCount.toLocaleString() },
  { key: 'roleCount', title: '角色数量', hint: '全部角色', format: (d) => d.roleCount.toLocaleString() },
  { key: 'permissionCount', title: '权限数量', hint: '资源 × 操作', format: (d) => d.permissionCount.toLocaleString() },
  { key: 'todayAuthzCount', title: '今日鉴权次数', hint: '今天 0 点起', format: (d) => d.todayAuthzCount.toLocaleString() },
  { key: 'successRate', title: '鉴权成功率', hint: '允许 / 全部判定', format: (d) => percent(d.successRate) },
  { key: 'failureRate', title: '鉴权失败率', hint: '拒绝 / 全部判定', format: (d) => percent(d.failureRate) },
  { key: 'p95LatencyMs', title: 'P95 延迟', hint: '今日判定耗时', format: (d) => d.p95LatencyMs.toFixed(2) + ' ms' },
  { key: 'p99LatencyMs', title: 'P99 延迟', hint: '今日判定耗时', format: (d) => d.p99LatencyMs.toFixed(2) + ' ms' },
  { key: 'qps', title: 'QPS', hint: '最近 60 秒', format: (d) => d.qps.toFixed(2) },
  { key: 'cacheHitRate', title: '缓存命中率', hint: '授权数据来自 Redis', format: (d) => percent(d.cacheHitRate) },
  { key: 'abnormalCount', title: '异常访问数量', hint: '登录失败 + 无效主体鉴权（点击查看）', format: (d) => d.abnormalCount.toLocaleString() },
]

async function load() {
  if (document.hidden) return
  data.value = await statsApi.dashboard()
}

function openAbnormal() {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  router.push({ path: '/audit-logs', query: { abnormal: '1', from: toLocalDateTime(today) } })
}

onMounted(() => {
  load()
  timer = setInterval(load, 10_000)
})
onUnmounted(() => clearInterval(timer))
</script>

<style scoped>
.col {
  margin-bottom: 16px;
}
.metric .title {
  color: #606266;
}
.metric .value {
  font-size: 28px;
  font-weight: 600;
  margin: 8px 0;
}
.metric .value.warn {
  color: #e6a23c;
}
.clickable {
  cursor: pointer;
}
</style>
