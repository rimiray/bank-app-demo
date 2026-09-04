import { request } from './client'
import type { AmountRequest, ApplyCreditRequest, CardResponse } from '../types'

const BASE = '/api/v1/cards'

export function getCards(): Promise<CardResponse[]> {
  return request<CardResponse[]>(BASE)
}

export function issueCard(): Promise<CardResponse> {
  return request<CardResponse>(BASE, { method: 'POST' })
}

export function topUpCard(cardId: string, amount: number): Promise<CardResponse> {
  const body: AmountRequest = { amount }
  return request<CardResponse>(`${BASE}/${cardId}/topup`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
}

export function purchaseCard(cardId: string, amount: number): Promise<CardResponse> {
  const body: AmountRequest = { amount }
  return request<CardResponse>(`${BASE}/${cardId}/purchase`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
}

export function applyCreditToCard(
  cardId: string,
  disbursementAmount: number,
  approvedCreditLimit: number,
): Promise<CardResponse> {
  const body: ApplyCreditRequest = { disbursementAmount, approvedCreditLimit }
  return request<CardResponse>(`${BASE}/${cardId}/apply-credit`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
}

export function closeCard(cardId: string): Promise<CardResponse> {
  return request<CardResponse>(`${BASE}/${cardId}/close`, { method: 'POST' })
}
