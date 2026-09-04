export interface CardResponse {
  id: string
  cardNumberMasked: string
  balance: number
  creditLimit: number
  activeDebt?: number
  loanPrincipal?: number
  currency: string
  status: string
  createdAt?: string
}

export interface AmountRequest {
  amount: number
}

export interface ApplyCreditRequest {
  disbursementAmount: number
  approvedCreditLimit: number
}

export interface CreditCalculationRequest {
  requestedAmount: number
  monthlyIncome: number
  termMonths: number
  aiCollateralValueEur?: number
}

export interface CreditCalculationResponse {
  monthlyPayment: number
  interestRate: number
  approvedLimit: number
  status: 'APPROVED' | 'REJECTED' | string
}

export interface CollateralEvaluationResponse {
  objectDetected: string
  condition: string
  estimatedValueEur: number
  maxCreditLimitEur: number
}

export interface ApiErrorBody {
  status?: number
  error?: string
  message?: string
}

export type ServiceKey =
  | 'cards'
  | 'credit'
  | 'collateral'
  | 'redis'
  | 'rabbitmq'
  | 'postgres'

export interface HealthService {
  name: string
  port: number
  up: boolean
}

export interface HealthResponse {
  checkedAt: string
  services: Record<ServiceKey, HealthService>
}

export type TabId = 'cards' | 'credit' | 'architecture'
