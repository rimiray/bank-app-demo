package com.bankapp.cardservice.config

import com.bankapp.cardservice.domain.Card
import com.bankapp.cardservice.domain.CardStatus
import com.bankapp.cardservice.repository.CardRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

/**
 * Seeds a small demo portfolio when the cards table is empty so the dashboard
 * is not blank on first visit (local Docker / live deploy).
 */
@Component
class DemoDataSeeder(
    private val cardRepository: CardRepository,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun run(args: ApplicationArguments) {
        if (cardRepository.count() > 0L) {
            return
        }

        val seeds = listOf(
            Card(
                cardNumberMasked = "**** **** **** 4242",
                balance = BigDecimal("1250.00"),
                creditLimit = BigDecimal("5000.00"),
                activeDebt = BigDecimal.ZERO,
                loanPrincipal = BigDecimal.ZERO,
                currency = "EUR",
                status = CardStatus.ACTIVE,
            ),
            Card(
                cardNumberMasked = "**** **** **** 1881",
                balance = BigDecimal("80.50"),
                creditLimit = BigDecimal("2500.00"),
                activeDebt = BigDecimal("420.00"),
                loanPrincipal = BigDecimal("400.00"),
                currency = "EUR",
                status = CardStatus.ACTIVE,
            ),
            Card(
                cardNumberMasked = "**** **** **** 9012",
                balance = BigDecimal("3000.00"),
                creditLimit = BigDecimal("7500.00"),
                activeDebt = BigDecimal.ZERO,
                loanPrincipal = BigDecimal.ZERO,
                currency = "EUR",
                status = CardStatus.ACTIVE,
            ),
        )

        cardRepository.saveAll(seeds)
        log.info("Seeded {} demo cards for empty database", seeds.size)
    }
}
