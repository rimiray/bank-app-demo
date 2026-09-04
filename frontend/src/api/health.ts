import { request } from './client'
import type { HealthResponse } from '../types'

export function fetchHealth(): Promise<HealthResponse> {
  return request<HealthResponse>('/api/health')
}
