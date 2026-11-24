package app.pwhs.universalinstaller.presentation.install.util

import android.os.Build

/**
 * Validation and device capability helpers for `--abi <abi>` install overrides.
 *
 * Some devices (for example 64-bit-only phones whose `SUPPORTED_32_BIT_ABIS` is empty)
 * and certain OEM ROMs reject `--abi` overrides with `INSTALL_FAILED_NO_MATCHING_ABIS`.
 */
object AbiOverrideUtil {

    val ALLOWED_ABIS: List<String> = listOf(
        "arm64-v8a",
        "armeabi-v7a",
        "armeabi",
        "x86_64",
        "x86",
    )

    private val ALLOWED_SET: Set<String> = ALLOWED_ABIS.toSet()

    /**
     * Returns a sanitized ABI string if [abi] is a known Android ABI, or `null` otherwise.
     * Prevents shell injection when interpolated into `pm install-create --abi <abi>`.
     */
    fun sanitize(abi: String?): String? {
        val trimmed = abi?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return trimmed.takeIf { it in ALLOWED_SET }
    }

    /** Device-reported ABIs in preference order. */
    fun deviceSupportedAbis(): List<String> = Build.SUPPORTED_ABIS.toList()

    /**
     * Builds the ordered list of selectable ABIs for the UI:
     * device-supported ABIs first, followed by any additional valid ABIs declared in the APK.
     */
    fun selectableAbis(
        apkAbis: List<String>,
        deviceAbis: List<String> = deviceSupportedAbis(),
    ): List<String> {
        val result = LinkedHashSet<String>()
        deviceAbis.mapNotNull { sanitize(it) }.forEach { result.add(it) }
        apkAbis.mapNotNull { sanitize(it) }.forEach { result.add(it) }
        return result.toList()
    }

    fun isSupportedByDevice(
        abi: String,
        deviceAbis: List<String> = deviceSupportedAbis(),
    ): Boolean = abi in deviceAbis

    fun isDeviceSingleAbi(
        deviceAbis: List<String> = deviceSupportedAbis(),
    ): Boolean = deviceAbis.size <= 1
}
