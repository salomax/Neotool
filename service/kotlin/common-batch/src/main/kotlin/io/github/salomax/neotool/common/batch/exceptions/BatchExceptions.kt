package io.github.salomax.neotool.common.batch.exceptions

/**
 * Exception thrown when message validation fails.
 * These errors should not be retried - send to DLQ immediately.
 */
class ValidationException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Exception thrown when message processing fails.
 * These errors may be transient and should be retried.
 */
open class ProcessingException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Exception thrown when message processing fails with a permanent error.
 * These errors should not be retried - send to DLQ immediately.
 *
 * Use this for errors like:
 * - Invalid payload structure
 * - Business rule violations
 * - Data format errors
 * - Any error that won't be resolved by retrying
 */
class PermanentProcessingException(
    message: String,
    cause: Throwable? = null,
) : ProcessingException(message, cause)

/**
 * Exception thrown when DLQ publishing fails.
 * This is a critical error that may result in message loss.
 */
class DlqPublishException(message: String, cause: Throwable? = null) : Exception(message, cause)
