package io.github.salomax.neotool.banking.domain.import.nfce

/**
 * Thrown when the NFC-e cannot be fetched (timeout, non-200 response, or network error).
 */
open class NfceUnavailableException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
