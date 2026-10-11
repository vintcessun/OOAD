import { describe, expect, it } from 'vitest'
import {
  FAILURE_LABEL, REASON_LABEL, USER_TYPE_LABEL, formatTime, label, maskPhone, percent, roleAtScope, splitPermissionCode,
  toLocalDateTime,
} from '@/utils/format'
import { meetsPolicy } from '@/utils/password'
import { heatmapOption } from '@/utils/heatmap'
import { clean } from '@/api'

describe('格式化', () => {
  it('列表中电话脱敏，详情页才显示完整号码', () => {
    expect(maskPhone('13812345678')).toBe('138****5678')
    expect(maskPhone(null)).toBe('—')
    expect(maskPhone('12345')).toBe('12345')
  })

  it('取值的中文说明，未知取值原样显示', () => {
    expect(label(USER_TYPE_LABEL, 'TEMPORARY')).toBe('临时用户')
    expect(label(REASON_LABEL, 'OUT_OF_DATA_SCOPE')).toContain('适用部门')
    expect(label(FAILURE_LABEL, 'PRIVILEGE_ESCALATION')).toContain('没有的权限')
    expect(label(USER_TYPE_LABEL, 'ALIEN')).toBe('ALIEN')
    expect(label(USER_TYPE_LABEL, null)).toBe('—')
  })

  it('比率、时间、权限码', () => {
    expect(percent(0.8333)).toBe('83.3%')
    expect(formatTime('2026-10-11T21:58:03.21')).toBe('2026-10-11 21:58:03')
    expect(formatTime(null)).toBe('—')
    expect(toLocalDateTime(new Date(2026, 0, 2, 3, 4, 5))).toBe('2026-01-02T03:04:05')
    expect(splitPermissionCode('oa:m01:f02:approve')).toEqual(['oa', 'm01', 'f02', 'approve'])
    expect(roleAtScope('部门经理', '道路工程分公司')).toBe('部门经理 @ 道路工程分公司')
  })

  it('口令策略与后端一致：至少 8 位，含大写、小写、数字与特殊字符', () => {
    expect(meetsPolicy('Passw0rd!')).toBe(true)
    expect(meetsPolicy('password')).toBe(false)
    expect(meetsPolicy('Pa0!')).toBe(false)
    expect(meetsPolicy('PASSWORD0!')).toBe(false)
    expect(meetsPolicy(null)).toBe(false)
  })

  it('请求参数去掉空值', () => {
    expect(clean({ a: '', b: null, c: undefined, d: 0, e: 'x', f: false })).toEqual({ d: 0, e: 'x', f: false })
  })

  it('热力图直接使用后端的 [操作, 系统, 次数]', () => {
    const o = heatmapOption({ from: '', to: '', actions: ['查看', '审批'], systems: ['OA'], cells: [[0, 0, 5], [1, 0, 2]] })
    expect(o.series[0].data).toEqual([[0, 0, 5], [1, 0, 2]])
    expect(o.visualMap.max).toBe(5)
    expect(o.tooltip.formatter({ value: [1, 0, 2] })).toContain('OA · 审批')
    expect(heatmapOption({ from: '', to: '', actions: [], systems: [], cells: [] }).visualMap.max).toBe(1)
  })
})
