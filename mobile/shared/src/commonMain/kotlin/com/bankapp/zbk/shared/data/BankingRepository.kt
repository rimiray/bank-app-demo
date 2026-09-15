package com.bankapp.zbk.shared.data

import com.bankapp.zbk.shared.dto.CardResponse
import com.bankapp.zbk.shared.dto.CreditApplicationRequest
import com.bankapp.zbk.shared.dto.CreditCalculationResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.RedirectResponseException
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.ServerResponseException
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
 *
 * Call sites should use [Result.isSuccess] / [Result.exceptionOrNull] so the UI can show
 * a network / HTTP failure instead of crashing.
 */
class BankingRepository(
    private val client: HttpClient,
    private val baseUrl: String = DEFAULT_BASE_URL,
) {
    suspend fun getCards(): Result<List<CardResponse>> =
        runCatchingApi {
            client.get("$baseUrl/cards").body()
        }

    suspend fun calculateCredit(request: CreditApplicationRequest): Result<CreditCalculationResponse> =
        runCatchingApi {
            client
                .post("$baseUrl/credits/calculate") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }.body()
        }

    private inline fun <T> runCatchingApi(block: () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (t: Throwable) {
            Result.failure(mapApiError(t))
        }

    private fun mapApiError(t: Throwable): BankingApiException =
        when (t) {
            is BankingApiException -> t
            is ClientRequestException ->
                BankingApiException(
                    status = t.response.status.value,
                    message = "Request rejected (${t.response.status.value})",
                    cause = t,
                )
            is RedirectResponseException ->
                BankingApiException(
                    status = t.response.status.value,
                    message = "Unexpected redirect (${t.response.status.value})",
                    cause = t,
                )
            is ServerResponseException ->
                BankingApiException(
                    status = t.response.status.value,
                    message = "Server error (${t.response.status.value})",
                    cause = t,
                )
            is ResponseException ->
                BankingApiException(
                    status = t.response.status.value,
                    message = "HTTP error (${t.response.status.value})",
                    cause = t,
                )
            else ->
                BankingApiException(
                    status = null,
                    message = t.message?.takeIf { it.isNotBlank() } ?: "Network request failed",
                    cause = t,
                )
        }

    companion object {
        const val DEFAULT_BASE_URL: String = "http://10.0.2.2:5173/api/v1"
    }
}
