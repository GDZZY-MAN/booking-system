export interface Viewer { shopId: number; employeeId: number | null; username: string; role: 'OWNER' | 'EMPLOYEE' }
export interface Shop { id: number; slug: string; name: string; description: string; published: boolean }
export interface Setup { hasActiveService: boolean; hasBookableEmployee: boolean }
export interface Offering { id: number; name: string; durationMinutes: number; active: boolean }
export interface Shift { weekday: number; startMinute: number; endMinute: number }
export interface Employee { id: number; name: string; active: boolean; serviceIds: number[]; shifts: Shift[]; closedDates: string[] }
export interface Slot { startsAt: string; endsAt: string }
export interface Receipt extends Slot { id: string; serviceName: string; employeeName: string; status: 'CONFIRMED' | 'CANCELLED' }
export interface Order extends Receipt { serviceId: number; employeeId: number; customerName: string; contact: string }
export interface PublicShop { name: string; description: string; services: Offering[] }
interface Csrf { token: string; headerName: string }

export class ApiError extends Error {
  constructor(readonly status: number, readonly code: string, message: string) {
    super(message)
  }
}

let csrfRequest: Promise<Csrf> | undefined

export async function api<T>(path: string, method = 'GET', body?: unknown): Promise<T> {
  try {
    const headers = new Headers({ Accept: 'application/json' })
    if (body !== undefined) headers.set('Content-Type', 'application/json')
    if (method !== 'GET') {
      const csrf = await (csrfRequest ??= api<Csrf>('/auth/csrf').catch(error => {
        csrfRequest = undefined
        throw error
      }))
      headers.set(csrf.headerName, csrf.token)
    }
    const response = await fetch('/api/v1' + path, {
      method, headers, body: body === undefined ? undefined : JSON.stringify(body),
      cache: 'no-store', signal: AbortSignal.timeout(12000),
    })
    if (!response.ok) {
      if (response.status === 401 || response.status === 403) csrfRequest = undefined
      const details = await response.json().catch(() => null)
      throw new ApiError(response.status, details?.code ?? 'HTTP_ERROR',
        details?.message ?? '服务暂时无法响应，请稍后重试。')
    }
    if (method === 'POST' && path.startsWith('/auth/')) csrfRequest = undefined
    return response.status === 204 ? undefined as T : await response.json()
  } catch (error) {
    if (error instanceof ApiError) throw error
    throw new ApiError(0, 'NETWORK_ERROR', '连接中断或超时，请检查网络后重试。')
  }
}

export function message(error: unknown): string {
  return error instanceof Error ? error.message : '操作失败，请重试。'
}

const localDay = new Intl.DateTimeFormat('sv-SE', {
  timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit',
})
const localTime = new Intl.DateTimeFormat('zh-CN', {
  timeZone: 'Asia/Shanghai', hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
})
export function today(offset = 0): string {
  return localDay.format(Date.now() + offset * 86_400_000)
}
export function dayOf(value: string): string { return localDay.format(new Date(value)) }
export function timeOf(value: string): string { return localTime.format(new Date(value)) }
export function timeRange(slot: Slot): string { return timeOf(slot.startsAt) + '–' + timeOf(slot.endsAt) }
