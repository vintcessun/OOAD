<template>
  <el-card>
    <div class="toolbar">
      <el-radio-group v-model="preset" @change="applyPreset">
        <el-radio-button value="1">今天</el-radio-button>
        <el-radio-button value="7">最近 7 天</el-radio-button>
        <el-radio-button value="30">最近 30 天</el-radio-button>
      </el-radio-group>
      <el-date-picker v-model="range" type="datetimerange" value-format="YYYY-MM-DDTHH:mm:ss" start-placeholder="从"
                      end-placeholder="到" @change="load" />
      <span class="spacer" />
      <span class="muted">单元格 = 时间段内该业务系统、该操作的成功鉴权次数；点击格子查看对应审计记录</span>
    </div>
    <div ref="chartEl" class="chart" data-test="heatmap-chart" />
    <p class="muted" data-test="heatmap-total">共 {{ total }} 次成功鉴权</p>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { statsApi } from '@/api'
import type { Heatmap } from '@/api/types'
import { toLocalDateTime } from '@/utils/format'
import { heatmapOption } from '@/utils/heatmap'

/** 权限使用热力图（docs/05-ui.md §3.11）：横轴操作、纵轴业务系统，数据来自审计日志里的真实鉴权事件。 */
const router = useRouter()
const chartEl = ref<HTMLDivElement>()
const preset = ref('7')
const range = ref<[string, string]>()
const total = ref(0)
let chart: { setOption: (o: object) => void; dispose: () => void; resize: () => void; on: (e: string, f: (p: unknown) => void) => void } | null = null
let data: Heatmap | null = null

function applyPreset() {
  const to = new Date()
  const from = new Date()
  if (preset.value === '1') from.setHours(0, 0, 0, 0)
  else from.setDate(from.getDate() - Number(preset.value))
  range.value = [toLocalDateTime(from), toLocalDateTime(to)]
  load()
}

async function load() {
  data = await statsApi.heatmap(range.value?.[0], range.value?.[1])
  total.value = data.cells.reduce((n, c) => n + c[2], 0)
  if (!chart && chartEl.value) {
    const echarts = await import('echarts')
    chart = echarts.init(chartEl.value)
    chart.on('click', (p) => {
      const range = data ? [data.from, data.to] : []
      router.push({ path: '/audit-logs', query: { eventType: 'AUTH_SUCCESS', from: range[0]?.slice(0, 19), to: range[1]?.slice(0, 19) } })
      void p
    })
  }
  chart?.setOption(heatmapOption(data))
}

const onResize = () => chart?.resize()
onMounted(() => {
  applyPreset()
  window.addEventListener('resize', onResize)
})
onUnmounted(() => {
  window.removeEventListener('resize', onResize)
  chart?.dispose()
})
</script>

<style scoped>
.chart {
  height: 560px;
}
</style>
