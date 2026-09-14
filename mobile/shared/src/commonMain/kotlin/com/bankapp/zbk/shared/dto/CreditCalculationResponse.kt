package com.bankapp.zbk.shared.dto

import kotlinx.serialization.Serializable

/**
 * Mirrors OpenAPI `CreditCalculationResponse`.
 * Credit-service JSON has no `path` (or other gateway) field — keep this DTO strict.
 */
@Serializable
data class CreditCalculationResponse(
    val monthlyPayment: Double,
    val interestRate: Double,
    val approvedLimit: Double,
    val status: String,
)
