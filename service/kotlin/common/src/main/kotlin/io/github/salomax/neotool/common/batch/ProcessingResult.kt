package io.github.salomax.neotool.common.batch

/**
 * Sealed class representing the result of message processing.
 * Used to avoid duplicate DLQ publishing.
 */
sealed class ProcessingResult {
    /**
     * Processing succeeded.
     */
    object Success : ProcessingResult()

    /**
     * Processing failed.
     *
     * @param alreadyPublishedToDlq Whether the message was already published to DLQ during processing
     * @param retryCount Number of retries attempted before failure
     */
    data class Failed(
        val alreadyPublishedToDlq: Boolean,
        val retryCount: Int,
    ) : ProcessingResult()
}
