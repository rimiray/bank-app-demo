package com.bankapp.zbk.ui.util

import com.bankapp.zbk.shared.data.BankingApiException

object ApiErrorMessages {
    const val NETWORK: String =
        "Network error. Please check your connection or try again later."

    fun from(error: Throwable): String {
        val api =
            generateSequence(error) { it.cause }
                .filterIsInstance<BankingApiException>()
                .firstOrNull()

        return when (val status = api?.status) {
            null -> NETWORK
            400 -> "Invalid request (400). Please check your input and try again."
            402 -> "Insufficient funds (402). Top up the card or lower the amount."
            404 -> "Not found (404). The card or resource may no longer exist."
            in 500..599 -> "Server error ($status). Please try again later."
            else ->
                api.message.takeIf { it.isNotBlank() }
                    ?: "Request failed ($status). Please try again."
        }
    }
}
