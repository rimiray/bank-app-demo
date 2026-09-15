package com.bankapp.zbk.shared.dto

import kotlinx.serialization.Serializable

/** Mirrors OpenAPI `ApplyCreditRequest`. */
@Serializable
data class ApplyCreditRequest(
    val disbursementAmount: Double,
    val approvedCreditLimit: Double,
)
