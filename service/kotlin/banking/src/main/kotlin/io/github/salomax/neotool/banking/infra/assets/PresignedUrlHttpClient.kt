package io.github.salomax.neotool.banking.infra.assets

/**
 * Abstraction for HTTP PUT/GET to presigned URLs (S3/R2).
 * Allows mocking in unit tests.
 */
interface PresignedUrlHttpClient {
    fun put(url: String, bytes: ByteArray, contentType: String)
    fun get(url: String): ByteArray
}
