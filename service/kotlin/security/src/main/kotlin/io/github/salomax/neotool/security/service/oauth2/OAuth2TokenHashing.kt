package io.github.salomax.neotool.security.service.oauth2

import jakarta.inject.Singleton
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

@Singleton
class OAuth2TokenHashing {
    private val random = SecureRandom()

    fun newOpaqueToken(byteSize: Int = 32): String {
        val bytes = ByteArray(byteSize)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun sha256Hex(value: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    fun pkceS256(codeVerifier: String): String =
        Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString(MessageDigest.getInstance("SHA-256").digest(codeVerifier.toByteArray(Charsets.US_ASCII)))

    fun constantTimeEquals(
        left: String,
        right: String,
    ): Boolean = MessageDigest.isEqual(left.toByteArray(Charsets.UTF_8), right.toByteArray(Charsets.UTF_8))
}
