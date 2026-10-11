import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AxiosError, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'

vi.mock('element-plus', () => ({ ElMessage: { error: vi.fn() } }))

import { ElMessage } from 'element-plus'
import { ApiError, classifyError, handleError, http, request, setAuthHooks } from '@/api/http'

function axiosError(status: number, body: unknown): AxiosError {
  const config = { headers: {} } as InternalAxiosRequestConfig
  return new AxiosError('x', 'ERR', config, null, { status, data: body, headers: {}, config, statusText: '' } as AxiosResponse)
}

const hooks = {
  getToken: vi.fn(() => 'tok'),
  onUnauthenticated: vi.fn(),
  onPasswordRequired: vi.fn(),
  onForbidden: vi.fn(),
}

beforeEach(() => {
  vi.clearAllMocks()
  setAuthHooks(hooks)
})

describe('统一错误处理（docs/05-ui.md §4.3）', () => {
  it('决策表', () => {
    expect(classifyError(401, 40101)).toBe('login')
    expect(classifyError(403, 40304)).toBe('password')
    expect(classifyError(403, 40301)).toBe('forbidden')
    expect(classifyError(403, 40302)).toBe('message')
    expect(classifyError(409, 40901)).toBe('message')
    expect(classifyError(0, 0)).toBe('message')
  })

  it('401 清令牌回登录页，不弹提示', () => {
    const e = handleError(axiosError(401, { code: 40101, message: '未登录' }), false)
    expect(e).toBeInstanceOf(ApiError)
    expect(e.code).toBe(40101)
    expect(hooks.onUnauthenticated).toHaveBeenCalled()
    expect(ElMessage.error).not.toHaveBeenCalled()
  })

  it('40304 跳改口令页', () => {
    handleError(axiosError(403, { code: 40304, message: '先改口令' }), false)
    expect(hooks.onPasswordRequired).toHaveBeenCalled()
  })

  it('40301 刷新本地权限并提示后端的说明', () => {
    handleError(axiosError(403, { code: 40301, message: '缺少权限 rbac:user:view' }), false)
    expect(hooks.onForbidden).toHaveBeenCalled()
    expect(ElMessage.error).toHaveBeenCalledWith('缺少权限 rbac:user:view')
  })

  it('40302 / 409 只提示；silent 时不提示', () => {
    handleError(axiosError(403, { code: 40302, message: '超出管理范围' }), false)
    expect(ElMessage.error).toHaveBeenCalledWith('超出管理范围')
    vi.clearAllMocks()
    handleError(axiosError(409, { code: 40901, message: '重复' }), true)
    expect(ElMessage.error).not.toHaveBeenCalled()
  })

  it('连不上服务器时给出可读的说明', () => {
    const e = handleError(new AxiosError('net'), false)
    expect(e.status).toBe(0)
    expect(e.message).toBe('无法连接权限中心')
    const e2 = handleError(axiosError(502, '<html>'), false)
    expect(e2.message).toBe('请求失败（HTTP 502）')
  })
})

describe('请求', () => {
  it('带上令牌并解包 ApiResponse.data；鉴权结论为拒绝时是正常结果', async () => {
    let auth: unknown
    http.defaults.adapter = async (config) => {
      auth = config.headers.get('Authorization')
      return { status: 200, data: { code: 0, message: 'success', data: { allowed: false } }, headers: {}, config, statusText: 'OK' }
    }
    const data = await request<{ allowed: boolean }>({ url: '/authz/check', method: 'POST' })
    expect(data.allowed).toBe(false)
    expect(auth).toBe('Bearer tok')
  })

  it('失败时抛出 ApiError', async () => {
    http.defaults.adapter = async () => {
      throw axiosError(404, { code: 40401, message: '用户不存在' })
    }
    await expect(request({ url: '/users/9' })).rejects.toMatchObject({ status: 404, code: 40401, message: '用户不存在' })
  })
})
