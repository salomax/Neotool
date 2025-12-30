package io.github.salomax.neotool.assets.exception

/**
 * Exception thrown when storage service (S3/R2/MinIO) is unavailable.
 *
 * This exception is used to indicate that the storage backend cannot be reached
 * or is experiencing issues. It should be mapped to STORAGE_UNAVAILABLE error code
 * in GraphQL responses.
 *
 * @param message Human-readable error message
 * @param cause Original exception that caused the storage unavailability
 */
class StorageUnavailableException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
