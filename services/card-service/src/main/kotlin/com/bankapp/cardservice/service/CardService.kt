package com.bankapp.cardservice.service

import com.bankapp.cardservice.domain.Card
import com.bankapp.cardservice.domain.CardStatus
import com.bankapp.cardservice.domain.Transaction
import com.bankapp.cardservice.domain.TransactionType
import com.bankapp.cardservice.dto.CardResponse
import com.bankapp.cardservice.exception.CannotCloseCardException
import com.bankapp.cardservice.exception.CardNotFoundException
import com.bankapp.cardservice.exception.InsufficientFundsException
import com.bankapp.cardservice.repository.CardRepository
import com.bankapp.cardservice.repository.TransactionRepository
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import kotlin.random.Random

@Service
class CardService(
    private val cardRepository: CardRepository,
    private val transactionRepository: TransactionRepository,
) {
    @Cacheable(cacheNames = [CARDS_CACHE])
    @Transactional(readOnly = true)
    fun getAllCards(): List<CardResponse> =
        cardRepository.findAll().map { it.toResponse() }

    @CacheEvict(cacheNames = [CARDS_CACHE], allEntries = true)
    @Transactional
    fun issueCard(): CardResponse {
        val lastFour = Random.nextInt(0, 10_000).toString().padStart(4, '0')
        val card = Card(
            cardNumberMasked = "**** **** **** $lastFour",
            balance = BigDecimal("0.0"),
            creditLimit = BigDecimal("5000.0"),
            activeDebt = BigDecimal("0.0"),
            currency = "EUR",
            status = CardStatus.ACTIVE,
        )
        return cardRepository.save(card).toResponse()
    }

    @CacheEvict(cacheNames = [CARDS_CACHE], allEntries = true)
    @Transactional
    fun topUp(cardId: String, amount: BigDecimal): CardResponse {
        val card = findActiveCard(cardId)
        var remaining = amount
        if (card.activeDebt > BigDecimal.ZERO) {
            val debtPayment = remaining.min(card.activeDebt)
            card.activeDebt = card.activeDebt.subtract(debtPayment)
            remaining = remaining.subtract(debtPayment)
        }
        card.balance = card.balance.add(remaining)
        transactionRepository.save(
            Transaction(cardId = card.id, amount = amount, type = TransactionType.TOPUP),
        )
        return cardRepository.save(card).toResponse()
    }

    @CacheEvict(cacheNames = [CARDS_CACHE], allEntries = true)
    @Transactional
    fun purchase(cardId: String, amount: BigDecimal): CardResponse {
        val card = findActiveCard(cardId)
        val available = card.balance.add(card.creditLimit)
        if (available < amount) {
            throw InsufficientFundsException()
        }
        if (card.balance >= amount) {
            card.balance = card.balance.subtract(amount)
        } else {
            val fromCredit = amount.subtract(card.balance)
            card.balance = BigDecimal.ZERO
            card.activeDebt = card.activeDebt.add(fromCredit)
        }
        transactionRepository.save(
            Transaction(cardId = card.id, amount = amount, type = TransactionType.PURCHASE),
        )
        return cardRepository.save(card).toResponse()
    }

    @CacheEvict(cacheNames = [CARDS_CACHE], allEntries = true)
    @Transactional
    fun closeCard(cardId: String): CardResponse {
        val card = findCard(cardId)
        if (card.activeDebt > BigDecimal.ZERO || card.balance < BigDecimal.ZERO) {
            throw CannotCloseCardException()
        }
        card.status = CardStatus.CLOSED
        return cardRepository.save(card).toResponse()
    }

    private fun findCard(cardId: String): Card =
        cardRepository.findById(cardId).orElseThrow { CardNotFoundException(cardId) }

    private fun findActiveCard(cardId: String): Card {
        val card = findCard(cardId)
        if (card.status != CardStatus.ACTIVE) {
            throw CardNotFoundException(cardId)
        }
        return card
    }

    private fun Card.toResponse() = CardResponse(
        id = id,
        cardNumberMasked = cardNumberMasked,
        balance = balance,
        creditLimit = creditLimit,
        currency = currency,
        status = status.name,
    )

    companion object {
        const val CARDS_CACHE = "cards"
    }
}
