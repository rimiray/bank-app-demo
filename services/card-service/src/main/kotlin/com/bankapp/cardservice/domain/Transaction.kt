package com.bankapp.cardservice.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "transactions")
class Transaction(
    @Id
    val id: String = UUID.randomUUID().toString(),

    @Column(nullable = false)
    val cardId: String = "",

    @Column(nullable = false, precision = 19, scale = 2)
    val amount: BigDecimal = BigDecimal.ZERO,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val type: TransactionType = TransactionType.TOPUP,

    @Column(nullable = false)
    val createdAt: Instant = Instant.now(),
)
