package app.pwhs.core.util

import android.content.Context
import app.pwhs.core.R
import java.util.Locale

/**
 * Result of a transfer progress estimation.
 *
 * @property speedBytesPerSec Estimated transfer speed in bytes per second.
 * @property etaSeconds Estimated seconds remaining until completion, or null if unknown / calculating.
 */
data class TransferEstimate(
    val speedBytesPerSec: Long = 0L,
    val etaSeconds: Long? = null,
)

/**
 * Calculates smoothed transfer speed and estimated remaining time (ETA).
 *
 * Uses Exponential Moving Average (EMA) to smooth out instant speed fluctuations,
 * providing stable and realistic ETA numbers even over jittery network connections (LAN, Wi-Fi, Bluetooth).
 */
class TransferEstimator(
    private val smoothingFactor: Double = 0.7,
    private val minSampleIntervalMs: Long = 250L,
) {
    private var startTimeMs: Long = 0L
    private var lastUpdateMs: Long = 0L
    private var lastBytes: Long = 0L
    private var currentSpeed: Double = 0.0

    fun reset() {
        startTimeMs = 0L
        lastUpdateMs = 0L
        lastBytes = 0L
        currentSpeed = 0.0
    }

    /**
     * Updates the estimator with the latest byte count and returns the updated estimate.
     */
    fun update(bytesTransferred: Long, totalBytes: Long): TransferEstimate {
        val now = System.currentTimeMillis()
        if (startTimeMs == 0L) {
            startTimeMs = now
            lastUpdateMs = now
            lastBytes = bytesTransferred
            return TransferEstimate(0L, null)
        }

        val elapsedMs = now - lastUpdateMs
        if (elapsedMs >= minSampleIntervalMs) {
            val deltaBytes = bytesTransferred - lastBytes
            if (deltaBytes >= 0) {
                val instantSpeed = deltaBytes * 1000.0 / elapsedMs
                currentSpeed = if (currentSpeed <= 0.0) {
                    instantSpeed
                } else {
                    smoothingFactor * currentSpeed + (1.0 - smoothingFactor) * instantSpeed
                }
            }
            lastUpdateMs = now
            lastBytes = bytesTransferred
        }

        val speedLong = currentSpeed.toLong().coerceAtLeast(0L)
        val remainingBytes = (totalBytes - bytesTransferred).coerceAtLeast(0L)
        val etaSeconds = if (speedLong > 0L && remainingBytes > 0L && bytesTransferred > 0L) {
            (remainingBytes / speedLong).coerceAtLeast(1L)
        } else if (remainingBytes == 0L && totalBytes > 0L) {
            0L
        } else {
            null
        }

        return TransferEstimate(speedLong, etaSeconds)
    }
}

/**
 * Formatting utilities for transfer speed and ETA strings.
 */
object TransferFormatter {

    /**
     * Formats bytes per second into human-readable rate (e.g., "2.4 MB/s", "350 KB/s").
     */
    fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0L) return ""
        val kb = bytesPerSec / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.US, "%.1f GB/s", gb)
            mb >= 1.0 -> String.format(Locale.US, "%.1f MB/s", mb)
            kb >= 1.0 -> String.format(Locale.US, "%.0f KB/s", kb)
            else -> "$bytesPerSec B/s"
        }
    }

    /**
     * Formats estimated remaining seconds into localized string using resource strings.
     */
    fun formatEta(context: Context, etaSeconds: Long?): String? {
        if (etaSeconds == null || etaSeconds < 0L) return null
        val hours = etaSeconds / 3600
        val minutes = (etaSeconds % 3600) / 60
        val seconds = etaSeconds % 60
        return when {
            hours > 0 -> context.getString(R.string.transfer_eta_hours_minutes, hours, minutes)
            minutes > 0 -> context.getString(R.string.transfer_eta_minutes_seconds, minutes, seconds)
            else -> context.getString(R.string.transfer_eta_seconds, seconds)
        }
    }

    /**
     * Compact, context-independent fallback format (e.g. "~15s", "~2m 10s").
     */
    fun formatEtaShort(etaSeconds: Long?): String? {
        if (etaSeconds == null || etaSeconds < 0L) return null
        val hours = etaSeconds / 3600
        val minutes = (etaSeconds % 3600) / 60
        val seconds = etaSeconds % 60
        return when {
            hours > 0 -> "~${hours}h ${minutes}m"
            minutes > 0 -> "~${minutes}m ${seconds}s"
            else -> "~${seconds}s"
        }
    }
}
