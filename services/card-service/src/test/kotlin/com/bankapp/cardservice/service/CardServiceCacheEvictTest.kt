package com.bankapp.cardservice.service

import com.bankapp.cardservice.domain.Card
import com.bankapp.cardservice.repository.CardRepository
import com.bankapp.cardservice.repository.TransactionRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.cache.interceptor.SimpleKey
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.EnableAspectJAutoProxy
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension

@ExtendWith(SpringExtension::class)
@ContextConfiguration(classes = [CardServiceCacheEvictTest.CacheTestConfig::class])
class CardServiceCacheEvictTest {

    @Autowired
    private lateinit var cardService: CardService

    @Autowired
    private lateinit var cardRepository: CardRepository

    @Autowired
    private lateinit var cacheManager: CacheManager

    @Test
    fun should_evictCardsCache_whenIssueCardIsCalled() {
        every { cardRepository.findAllInDbTableOrder() } returns emptyList()
        every { cardRepository.save(any()) } answers { firstArg() }

        // warm cache
        assertThat(cardService.getAllCards()).isEmpty()
        verify(exactly = 1) { cardRepository.findAllInDbTableOrder() }

        // cached hit — repository not called again
        assertThat(cardService.getAllCards()).isEmpty()
        verify(exactly = 1) { cardRepository.findAllInDbTableOrder() }

        val cache = cacheManager.getCache(CardService.CARDS_CACHE)
        assertThat(cache).isNotNull
        assertThat(cache!!.get(SimpleKey.EMPTY)).isNotNull

        cardService.issueCard()

        assertThat(cache.get(SimpleKey.EMPTY)).isNull()

        // after evict, list load hits repository again
        cardService.getAllCards()
        verify(exactly = 2) { cardRepository.findAllInDbTableOrder() }
    }

    @Configuration
    @EnableCaching
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    class CacheTestConfig {
        @Bean
        fun cacheManager(): CacheManager = ConcurrentMapCacheManager(CardService.CARDS_CACHE)

        @Bean
        fun cardRepository(): CardRepository = mockk(relaxed = true)

        @Bean
        fun transactionRepository(): TransactionRepository = mockk(relaxed = true)

        @Bean
        fun cardService(
            cardRepository: CardRepository,
            transactionRepository: TransactionRepository,
        ): CardService = CardService(cardRepository, transactionRepository)
    }
}
