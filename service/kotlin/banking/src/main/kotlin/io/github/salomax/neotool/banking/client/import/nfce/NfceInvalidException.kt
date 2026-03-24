package io.github.salomax.neotool.banking.client.import.nfce

/**
 * Thrown when the NFC-e XML is malformed or the NF-e is cancelled.
 */
open class NfceInvalidException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
