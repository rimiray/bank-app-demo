package com.bankapp.zbk.shared.data

/**
 * Failure surfaced to UI from [BankingRepository].
 * [status] is set for HTTP errors; null usually means transport / offline failure.
 */
class BankingApiException(
    val status: Int? = null,
    override val message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
