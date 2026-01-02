package io.github.salomax.neotool.security.http

import io.github.salomax.neotool.common.security.config.JwtConfig
import io.github.salomax.neotool.security.key.SecurityKeyManagerFactory
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.security.interfaces.RSAPublicKey
import java.util.Base64

/**
 * Controller for JWKS (JSON Web Key Set) endpoint.
 * Exposes public keys for JWT validation via standard JWKS endpoint.
 *
 * Endpoint: GET /.well-known/jwks.json
 *
 * @see https://tools.ietf.org/html/rfc7517
 */
@Singleton
@Controller("/.well-known")
class JwksController(
    private val jwtConfig: JwtConfig,
    private val keyManagerFactory: SecurityKeyManagerFactory,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * JWKS response DTO.
     */
    data class JwksResponse(
        val keys: List<Jwk>,
    )

    /**
     * JSON Web Key (JWK) DTO.
     */
    data class Jwk(
        val kty: String, // Key type
        val kid: String, // Key ID
        val use: String, // Key use
        val alg: String, // Algorithm
        val n: String, // RSA modulus (base64url)
        val e: String, // RSA exponent (base64url)
    )

    /**
     * Get JWKS endpoint.
     * Returns public keys in JWKS format for JWT validation.
     */
    @Get("/jwks.json")
    @Produces(MediaType.APPLICATION_JSON)
    fun getJwks(): HttpResponse<JwksResponse> {
        if (!jwtConfig.jwksEnabled) {
            logger.debug { "JWKS endpoint is disabled" }
            return HttpResponse.notFound()
        }

        val keyManager = keyManagerFactory.getKeyManager()
        val keyId = jwtConfig.keyId ?: "kid-1"
        val publicKey = keyManager.getPublicKey(keyId)

        if (publicKey == null) {
            logger.warn { "JWKS endpoint requested but no public key configured for keyId: $keyId" }
            return HttpResponse.serverError()
        }

        if (publicKey !is RSAPublicKey) {
            logger.error { "Public key is not an RSA key" }
            return HttpResponse.serverError()
        }

        val jwk = rsaPublicKeyToJwk(publicKey, keyId)

        val response = JwksResponse(keys = listOf(jwk))

        return HttpResponse
            .ok(response)
            .header("Cache-Control", "public, max-age=300") // Cache for 5 minutes
    }

    /**
     * Convert RSA public key to JWK format.
     */
    private fun rsaPublicKeyToJwk(
        publicKey: RSAPublicKey,
        keyId: String,
    ): Jwk {
        val modulus = publicKey.modulus
        val exponent = publicKey.publicExponent

        // Convert BigInteger to unsigned byte array (big-endian)
        val modulusBytes = modulus.toByteArray()
        val exponentBytes = exponent.toByteArray()

        // Remove leading zero byte if present (BigInteger.toByteArray() may add it for positive numbers)
        val n = base64UrlEncode(removeLeadingZero(modulusBytes))
        val e = base64UrlEncode(removeLeadingZero(exponentBytes))

        return Jwk(
            kty = "RSA",
            kid = keyId,
            use = "sig", // Signature
            alg = "RS256",
            n = n,
            e = e,
        )
    }

    /**
     * Remove leading zero byte from byte array if present.
     */
    private fun removeLeadingZero(bytes: ByteArray): ByteArray =
        if (bytes.isNotEmpty() && bytes[0].toInt() == 0) {
            bytes.sliceArray(1 until bytes.size)
        } else {
            bytes
        }

    /**
     * Encode bytes to base64url (RFC 4648 Section 5).
     * Base64url uses '-' instead of '+' and '_' instead of '/', and omits padding.
     */
    private fun base64UrlEncode(bytes: ByteArray): String =
        Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString(bytes)
}
