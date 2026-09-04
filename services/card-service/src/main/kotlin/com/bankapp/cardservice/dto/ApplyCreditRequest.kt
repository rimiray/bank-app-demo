package com.bankapp.cardservice.dto

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal

data class ApplyCreditRequest(
    @field:NotNull
    @field:DecimalMin(value = "0.01", inclusive = true)
    val disbursementAmount: BigDecimal,

    @field:NotNull
    @field:DecimalMin(value = "0.01", inclusive = true)
    val approvedCreditLimit: BigDecimal,
)
