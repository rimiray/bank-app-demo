package com.bankapp.zbk.shared.network

import io.ktor.client.HttpClient

expect fun createHttpClient(): HttpClient
