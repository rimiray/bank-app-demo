package com.bankapp.cardservice.dto

import java.math.BigDecimal

data class CardResponse(
    val id: String,
    val cardNumberMasked: String,
    val balance: BigDecimal,
    val creditLimit: BigDecimal,
    val currency: String,
    val status: String,
)
