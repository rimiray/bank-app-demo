package com.bankapp.cardservice.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.util.UUID

@Entity
@Table(name = "cards")
class Card(
    @Id
    val id: String = UUID.randomUUID().toString(),

    @Column(nullable = false)
    var cardNumberMasked: String = "",

    @Column(nullable = false, precision = 19, scale = 2)
    var balance: BigDecimal = BigDecimal.ZERO,

    @Column(nullable = false, precision = 19, scale = 2)
    var creditLimit: BigDecimal = BigDecimal("5000.00"),

    @Column(nullable = false, precision = 19, scale = 2)
    var activeDebt: BigDecimal = BigDecimal.ZERO,

    @Column(nullable = false)
    var currency: String = "EUR",

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: CardStatus = CardStatus.ACTIVE,
)
