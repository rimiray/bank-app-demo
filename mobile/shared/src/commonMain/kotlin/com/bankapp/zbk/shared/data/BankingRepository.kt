package com.bankapp.zbk.shared.data

import com.bankapp.zbk.shared.dto.CardResponse
import com.bankapp.zbk.shared.dto.CreditApplicationRequest
import com.bankapp.zbk.shared.dto.CreditCalculationResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

/**
 * API access for ZBK Credit Companion.
 *
 * Default [baseUrl] targets the Vite/Nginx frontend proxy from the Android emulator
 * (`10.0.2.2` → host loopback), same paths as the web dashboard (`/api/v1/...`).
 */
class BankingRepository(
    private val client: HttpClient,
    private val baseUrl: String = DEFAULT_BASE_URL,
) {
    suspend fun getCards(): List<CardResponse> =
        client.get("$baseUrl/cards").body()

    suspend fun calculateCredit(request: CreditApplicationRequest): CreditCalculationResponse =
        client
            .post("$baseUrl/credits/calculate") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()

    companion object {
        const val DEFAULT_BASE_URL: String = "http://10.0.2.2:5173/api/v1"
    }
}
