package com.bankapp.zbk.shared.di

import com.bankapp.zbk.shared.data.BankingRepository
import com.bankapp.zbk.shared.network.createHttpClient
import org.koin.dsl.module

fun sharedModule(
    baseUrl: String = BankingRepository.DEFAULT_BASE_URL,
) = module {
    single { createHttpClient() }
    single { BankingRepository(client = get(), baseUrl = baseUrl) }
}
