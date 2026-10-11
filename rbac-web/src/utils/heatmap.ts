import type { Heatmap } from '@/api/types'

/**
 * 热力图的 ECharts 配置。后端的 cells 已是 [操作下标, 系统下标, 次数]，正是 ECharts 热力图的数据格式。
 */
export function heatmapOption(h: Heatmap) {
  const max = h.cells.reduce((m, c) => Math.max(m, c[2]), 0)
  return {
    tooltip: {
      position: 'top',
      formatter: (p: { value: [number, number, number] }) =>
        `${h.systems[p.value[1]]} · ${h.actions[p.value[0]]}<br/>成功鉴权 ${p.value[2]} 次`,
    },
    grid: { left: 170, right: 40, top: 20, bottom: 80 },
    xAxis: { type: 'category', data: h.actions, splitArea: { show: true } },
    yAxis: { type: 'category', data: h.systems, splitArea: { show: true } },
    visualMap: {
      min: 0, max: Math.max(max, 1), calculable: true, orient: 'horizontal', left: 'center', bottom: 10,
      text: ['高频', '低频'],
    },
    series: [{ name: '成功鉴权次数', type: 'heatmap', data: h.cells, label: { show: true } }],
  }
}
