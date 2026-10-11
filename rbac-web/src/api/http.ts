import axios, { AxiosError, type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import type { ApiResponse } from './types'

/** 后端返回的业务错误：HTTP 状态 + 错误码 + 中文说明（docs/04-api.md §1.3）。 */
export class ApiError extends Error {
  constructor(public status: number, public code: number, message: string) {
    super(message)
  }
}

/**
 * 统一错误处理的决策表（docs/05-ui.md §4.3）。只做判断，不做副作用，便于单测。
 * - login：令牌失效，清令牌回登录页
 * - password：首次登录须先改口令
 * - forbidden：缺少操作权限，刷新本地权限状态
 * - message：其余错误只提示后端给出的说明
 */
export type ErrorAction = 'login' | 'password' | 'forbidden' | 'message'

export function classifyError(status: number, code: number): ErrorAction {
  if (status === 401) return 'login'
  if (status === 403 && code === 40304) return 'password'
  if (status === 403 && code === 40301) return 'forbidden'
  return 'message'
}

export interface AuthHooks {
  getToken: () => string | null
  onUnauthenticated: () => void
  onPasswordRequired: () => void
  onForbidden: () => void
}

let hooks: AuthHooks = {
  getToken: () => null,
  onUnauthenticated: () => {},
  onPasswordRequired: () => {},
  onForbidden: () => {},
}

/** 由 main.ts 注入：http 层不直接依赖 store 与 router，避免循环引用。 */
export function setAuthHooks(h: AuthHooks) {
  hooks = h
}

export interface RequestOptions extends AxiosRequestConfig {
  /** 不弹出错误提示（调用方自己处理），401/40304 的跳转仍然执行 */
  silent?: boolean
}

export const http = axios.create({ baseURL: '/api/v1', timeout: 600_000 })

http.interceptors.request.use((config) => {
  const token = hooks.getToken()
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`)
  }
  return config
})

/** 发请求并解包 ApiResponse.data；失败时抛 ApiError。 */
export async function request<T>(options: RequestOptions): Promise<T> {
  try {
    const resp = await http.request<ApiResponse<T>>(options)
    return resp.data.data
  } catch (e) {
    throw handleError(e, options.silent ?? false)
  }
}

export function handleError(e: unknown, silent: boolean): ApiError {
  const err = e as AxiosError<ApiResponse<unknown>>
  const status = err.response?.status ?? 0
  const body = err.response?.data
  const code = body && typeof body === 'object' ? body.code ?? 0 : 0
  const message = (body && typeof body === 'object' && body.message) || (status === 0 ? '无法连接权限中心' : `请求失败（HTTP ${status}）`)
  const apiError = new ApiError(status, code, message)
  switch (classifyError(status, code)) {
    case 'login':
      hooks.onUnauthenticated()
      break
    case 'password':
      hooks.onPasswordRequired()
      break
    case 'forbidden':
      hooks.onForbidden()
      if (!silent) ElMessage.error(message)
      break
    default:
      if (!silent) ElMessage.error(message)
  }
  return apiError
}

export const get = <T>(url: string, params?: object, opts: RequestOptions = {}) =>
  request<T>({ ...opts, method: 'GET', url, params })
export const post = <T>(url: string, data?: unknown, opts: RequestOptions = {}) =>
  request<T>({ ...opts, method: 'POST', url, data })
export const patch = <T>(url: string, data?: unknown, opts: RequestOptions = {}) =>
  request<T>({ ...opts, method: 'PATCH', url, data })
export const del = <T>(url: string, params?: object, opts: RequestOptions = {}) =>
  request<T>({ ...opts, method: 'DELETE', url, params })
