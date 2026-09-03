package com.bankapp.cardservice.dto

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal

data class AmountRequest(
    @field:NotNull
    @field:DecimalMin(value = "0.01", inclusive = true)
    val amount: BigDecimal,
)
