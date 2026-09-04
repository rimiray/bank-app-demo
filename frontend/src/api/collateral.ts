import { request } from './client'
import type { CollateralEvaluationResponse } from '../types'

export function evaluateCollateral(file: File): Promise<CollateralEvaluationResponse> {
  const form = new FormData()
  form.append('file', file)
  return request<CollateralEvaluationResponse>('/api/v1/collateral/evaluate', {
    method: 'POST',
    body: form,
  })
}
