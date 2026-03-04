package io.github.salomax.neotool.banking.domain.import.nfce

/**
 * Raw HTTP response for NFC-e fetch (status and body only).
 */
data class NfceHttpResponse(
    val statusCode: Int,
    val body: String,
)
