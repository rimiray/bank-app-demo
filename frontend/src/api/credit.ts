import { request } from './client'
import type { CreditCalculationRequest, CreditCalculationResponse } from '../types'

export function calculateCredit(
  payload: CreditCalculationRequest,
): Promise<CreditCalculationResponse> {
  return request<CreditCalculationResponse>('/api/v1/credits/calculate', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}
