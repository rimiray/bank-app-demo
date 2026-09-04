import type { ApiErrorBody } from '../types'

export class ApiError extends Error {
  status: number
  body?: ApiErrorBody

  constructor(status: number, message: string, body?: ApiErrorBody) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.body = body
  }
}

export async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, init)
  const contentType = response.headers.get('content-type') ?? ''
  const isJson = contentType.includes('application/json')
  const payload = isJson ? await response.json().catch(() => undefined) : undefined

  if (!response.ok) {
    const body = payload as ApiErrorBody | undefined
    throw new ApiError(
      response.status,
      body?.message ?? `Request failed (${response.status})`,
      body,
    )
  }

  return payload as T
}

export function money(value: number | string | null | undefined, currency = 'EUR'): string {
  const n = typeof value === 'string' ? Number(value) : (value ?? 0)
  return new Intl.NumberFormat('de-DE', {
    style: 'currency',
    currency,
    minimumFractionDigits: 2,
  }).format(Number.isFinite(n) ? n : 0)
}
