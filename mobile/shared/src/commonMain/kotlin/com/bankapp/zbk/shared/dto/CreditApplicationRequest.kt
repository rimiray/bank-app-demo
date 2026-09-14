package com.bankapp.zbk.shared.dto

import kotlinx.serialization.Serializable

/**
 * Request body for `POST /credits/calculate`.
 * Named for the credit-application flow; fields match OpenAPI `CreditCalculationRequest`.
 */
@Serializable
data class CreditApplicationRequest(
    val requestedAmount: Double,
    val monthlyIncome: Double,
    val termMonths: Int,
    val aiCollateralValueEur: Double? = null,
)
