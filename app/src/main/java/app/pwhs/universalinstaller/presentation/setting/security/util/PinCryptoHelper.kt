package app.pwhs.universalinstaller.presentation.setting.security.util

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Utility for hashing and verifying PIN codes securely using SHA-256 and cryptographic salts.
 */
object PinCryptoHelper {

    private const val SALT_LENGTH_BYTES = 16

    /**
     * Generates a cryptographically secure random salt encoded as a hexadecimal string.
     */
    fun generateSalt(): String {
        val random = SecureRandom()
        val saltBytes = ByteArray(SALT_LENGTH_BYTES)
        random.nextBytes(saltBytes)
        return saltBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Computes the SHA-256 hash of the given PIN combined with the salt.
     */
    fun hashPin(pin: String, salt: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val combined = "$salt:$pin".toByteArray(Charsets.UTF_8)
        val digest = md.digest(combined)
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Verifies whether the provided [inputPin] matches the [storedHash] using constant-time comparison.
     */
    fun verifyPin(inputPin: String, storedHash: String, storedSalt: String): Boolean {
        if (inputPin.isBlank() || storedHash.isBlank() || storedSalt.isBlank()) return false
        val computedHash = hashPin(inputPin, storedSalt)
        return MessageDigest.isEqual(
            computedHash.toByteArray(Charsets.UTF_8),
            storedHash.toByteArray(Charsets.UTF_8),
        )
    }
}
