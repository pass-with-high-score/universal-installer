package app.pwhs.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import timber.log.Timber

object VirusTotalClientFactory {
    fun create(isDebug: Boolean = false): HttpClient {
        return HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = 5 * 60 * 1000L   // 5 minutes — covers 32 MB upload over 3G
                connectTimeoutMillis = 30_000L
                socketTimeoutMillis = 5 * 60 * 1000L     // match request timeout for large uploads
            }
            if (isDebug) {
                install(Logging) {
                    logger = object : Logger {
                        override fun log(message: String) {
                            Timber.tag("KtorHttp").d(message)
                        }
                    }
                    level = LogLevel.HEADERS
                }
            }
        }
    }
}
