package com.bankapp.zbk.ui.util

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

fun formatMoney(
    amount: Double,
    currencyCode: String = "EUR",
): String {
    val format = NumberFormat.getCurrencyInstance(Locale.GERMANY)
    format.currency =
        runCatching { Currency.getInstance(currencyCode) }
            .getOrElse { Currency.getInstance("EUR") }
    return format.format(amount)
}
