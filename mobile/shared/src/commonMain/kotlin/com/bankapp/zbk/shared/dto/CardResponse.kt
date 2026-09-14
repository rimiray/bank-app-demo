package com.bankapp.zbk.shared.dto

import kotlinx.serialization.Serializable

/** Mirrors OpenAPI `CardResponse` (`docs/api/openapi.yaml`). */
@Serializable
data class CardResponse(
    val id: String,
    val cardNumberMasked: String,
    val balance: Double,
    val creditLimit: Double,
    val activeDebt: Double? = null,
    val loanPrincipal: Double? = null,
    val currency: String,
    val status: String,
    val createdAt: String? = null,
)
