package com.bankapp.cardservice.service

import com.bankapp.cardservice.domain.Card
import com.bankapp.cardservice.domain.CardStatus
import com.bankapp.cardservice.exception.CannotCloseCardException
import com.bankapp.cardservice.exception.InsufficientFundsException
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

class CardServicePurchaseTest {

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
    fun should_throwInsufficientFunds_andNotChangeActiveDebt_whenBalanceAndLimitInsufficient() {
        val card = activeCard(
            balance = BigDecimal("100.00"),
            creditLimit = BigDecimal("50.00"),
            activeDebt = BigDecimal("10.00"),
        )
        every { cardRepository.findById(card.id) } returns Optional.of(card)

        assertThatThrownBy {
            cardService.purchase(card.id, BigDecimal("200.00"))
        }.isInstanceOf(InsufficientFundsException::class.java)

        assertThat(card.activeDebt).isEqualByComparingTo("10.00")
        assertThat(card.balance).isEqualByComparingTo("100.00")
        verify(exactly = 0) { transactionRepository.save(any()) }
        verify(exactly = 0) { cardRepository.save(any()) }
    }

    @Test
    fun should_throwInsufficientFunds_whenDebtAlreadyAtCreditLimit_andAmountExceedsBalance() {
        // Post apply-credit shape: cash and debt both rose; unused revolving headroom is 0.
        val card = activeCard(
            balance = BigDecimal("10000.00"),
            creditLimit = BigDecimal("10000.00"),
            activeDebt = BigDecimal("10000.00"),
        )
        every { cardRepository.findById(card.id) } returns Optional.of(card)

        assertThatThrownBy {
            cardService.purchase(card.id, BigDecimal("10000.01"))
        }.isInstanceOf(InsufficientFundsException::class.java)

        assertThat(card.balance).isEqualByComparingTo("10000.00")
        assertThat(card.activeDebt).isEqualByComparingTo("10000.00")
        verify(exactly = 0) { transactionRepository.save(any()) }
        verify(exactly = 0) { cardRepository.save(any()) }
    }

    @Test
    fun should_spendOnlyCash_whenDebtAlreadyAtCreditLimit_andAmountWithinBalance() {
        val card = activeCard(
            balance = BigDecimal("10000.00"),
            creditLimit = BigDecimal("10000.00"),
            activeDebt = BigDecimal("10000.00"),
        )
        every { cardRepository.findById(card.id) } returns Optional.of(card)
        every { transactionRepository.save(any()) } answers { firstArg() }
        every { cardRepository.save(any()) } answers { firstArg() }

        val result = cardService.purchase(card.id, BigDecimal("2500.00"))

        assertThat(card.balance).isEqualByComparingTo("7500.00")
        assertThat(card.activeDebt).isEqualByComparingTo("10000.00")
        assertThat(result.balance).isEqualByComparingTo("7500.00")
        assertThat(result.activeDebt).isEqualByComparingTo("10000.00")
    }

    @Test
    fun should_allowPurchaseUpToUnusedCreditHeadroom_whenDebtExists() {
        // available = 50 + max(0, 100 - 80) = 70
        val card = activeCard(
            balance = BigDecimal("50.00"),
            creditLimit = BigDecimal("100.00"),
            activeDebt = BigDecimal("80.00"),
        )
        every { cardRepository.findById(card.id) } returns Optional.of(card)
        every { transactionRepository.save(any()) } answers { firstArg() }
        every { cardRepository.save(any()) } answers { firstArg() }

        val result = cardService.purchase(card.id, BigDecimal("70.00"))

        assertThat(card.balance).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(card.activeDebt).isEqualByComparingTo("100.00")
        assertThat(result.activeDebt).isEqualByComparingTo("100.00")
    }

    @Test
    fun should_throwInsufficientFunds_whenAmountExceedsUnusedCreditHeadroom_evenIfBalancePlusFullLimitWouldCover() {
        // Old buggy formula: 100 + 50 = 150 would accept 145 and push debt past the limit.
        // Correct: 100 + max(0, 50 - 10) = 140 → reject.
        val card = activeCard(
            balance = BigDecimal("100.00"),
            creditLimit = BigDecimal("50.00"),
            activeDebt = BigDecimal("10.00"),
        )
        every { cardRepository.findById(card.id) } returns Optional.of(card)

        assertThatThrownBy {
            cardService.purchase(card.id, BigDecimal("145.00"))
        }.isInstanceOf(InsufficientFundsException::class.java)

        assertThat(card.balance).isEqualByComparingTo("100.00")
        assertThat(card.activeDebt).isEqualByComparingTo("10.00")
        verify(exactly = 0) { transactionRepository.save(any()) }
    }

    @Test
    fun should_setBalanceToZeroAndIncreaseActiveDebt_whenPurchaseExceedsBalanceWithinLimit() {
        val card = activeCard(
            balance = BigDecimal("30.00"),
            creditLimit = BigDecimal("5000.00"),
            activeDebt = BigDecimal.ZERO,
        )
        every { cardRepository.findById(card.id) } returns Optional.of(card)
        every { transactionRepository.save(any()) } answers { firstArg() }
        every { cardRepository.save(any()) } answers { firstArg() }

        val result = cardService.purchase(card.id, BigDecimal("100.00"))

        assertThat(card.balance).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(card.activeDebt).isEqualByComparingTo("70.00")
        assertThat(result.balance).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(result.activeDebt).isEqualByComparingTo("70.00")
        verify(exactly = 1) { transactionRepository.save(any()) }
        verify(exactly = 1) { cardRepository.save(card) }
    }

    private fun activeCard(
        balance: BigDecimal,
        creditLimit: BigDecimal,
        activeDebt: BigDecimal,
    ) = Card(
        cardNumberMasked = "**** **** **** 1111",
        balance = balance,
        creditLimit = creditLimit,
        activeDebt = activeDebt,
        status = CardStatus.ACTIVE,
    )
}
