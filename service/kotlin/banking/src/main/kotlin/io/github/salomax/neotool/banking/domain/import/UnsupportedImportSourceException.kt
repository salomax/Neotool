package io.github.salomax.neotool.banking.domain.import

/**
 * Thrown when the import source cannot be determined from file content or name.
 */
open class UnsupportedImportSourceException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
