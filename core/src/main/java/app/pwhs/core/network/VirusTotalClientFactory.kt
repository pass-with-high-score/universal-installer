package app.pwhs.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import timber.log.Timber

object VirusTotalClientFactory {
    fun create(isDebug: Boolean = false): HttpClient {
        return HttpClient(OkHttp) {
            install(HttpTimeout) {
                requestTimeoutMillis = 5 * 60 * 1000L   // 5 minutes
                connectTimeoutMillis = 30_000L          // 30 seconds
                socketTimeoutMillis = 5 * 60 * 1000L    // 5 minutes
            }
            if (isDebug) {
                install(Logging) {
                    logger = object : Logger {
                        override fun log(message: String) {
                            Timber.tag("KtorHttp").d(message)
                        }
                    }
                    level = LogLevel.HEADERS
                    sanitizeHeader { header -> header.equals("x-apikey", ignoreCase = true) }
                }
            }
        }
    }
}
