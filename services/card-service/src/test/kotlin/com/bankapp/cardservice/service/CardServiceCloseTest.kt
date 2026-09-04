package com.bankapp.cardservice.service

import com.bankapp.cardservice.domain.Card
import com.bankapp.cardservice.domain.CardStatus
import com.bankapp.cardservice.exception.CannotCloseCardException
import com.bankapp.cardservice.repository.CardRepository
import com.bankapp.cardservice.repository.TransactionRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.Optional

class CardServiceCloseTest {

    private lateinit var cardRepository: CardRepository
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var cardService: CardService

    @BeforeEach
    fun setUp() {
        cardRepository = mockk()
        transactionRepository = mockk()
        cardService = CardService(cardRepository, transactionRepository)
    }

    @Test
    fun should_rejectClose_andKeepActive_whenActiveDebtGreaterThanZero() {
        val card = Card(
            cardNumberMasked = "**** **** **** 2222",
            balance = BigDecimal.ZERO,
            activeDebt = BigDecimal("25.00"),
            status = CardStatus.ACTIVE,
        )
        every { cardRepository.findById(card.id) } returns Optional.of(card)

        assertThatThrownBy { cardService.closeCard(card.id) }
            .isInstanceOf(CannotCloseCardException::class.java)

        assertThat(card.status).isEqualTo(CardStatus.ACTIVE)
        verify(exactly = 0) { cardRepository.save(any()) }
    }

    @Test
    fun should_rejectClose_whenBalanceIsNegative() {
        val card = Card(
            cardNumberMasked = "**** **** **** 3333",
            balance = BigDecimal("-0.01"),
            activeDebt = BigDecimal.ZERO,
            status = CardStatus.ACTIVE,
        )
        every { cardRepository.findById(card.id) } returns Optional.of(card)

        assertThatThrownBy { cardService.closeCard(card.id) }
            .isInstanceOf(CannotCloseCardException::class.java)

        assertThat(card.status).isEqualTo(CardStatus.ACTIVE)
        verify(exactly = 0) { cardRepository.save(any()) }
    }

    @Test
    fun should_closeCard_whenActiveDebtIsZeroAndBalanceNonNegative() {
        val card = Card(
            cardNumberMasked = "**** **** **** 4444",
            balance = BigDecimal("15.00"),
            activeDebt = BigDecimal.ZERO,
            status = CardStatus.ACTIVE,
        )
        every { cardRepository.findById(card.id) } returns Optional.of(card)
        every { cardRepository.save(any()) } answers { firstArg() }

        val result = cardService.closeCard(card.id)

        assertThat(card.status).isEqualTo(CardStatus.CLOSED)
        assertThat(result.status).isEqualTo("CLOSED")
        verify(exactly = 1) { cardRepository.save(card) }
    }
}
