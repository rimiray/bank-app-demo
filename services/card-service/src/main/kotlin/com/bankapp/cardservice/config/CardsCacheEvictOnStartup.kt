package com.bankapp.cardservice.config

import com.bankapp.cardservice.service.CardService
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.cache.CacheManager
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/** Drop stale Redis card lists after schema/query-order changes. */
@Component
@Order(0)
class CardsCacheEvictOnStartup(
    private val cacheManager: CacheManager,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        cacheManager.getCache(CardService.CARDS_CACHE)?.clear()
    }
}
