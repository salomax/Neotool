package io.github.salomax.neotool.common.batch

/**
 * Service interface for publishing messages to Dead Letter Queue.
 *
 * @param TMessage The message type
 */
interface DlqPublisherService<TMessage> {
    /**
     * Publish a failed message to DLQ.
     *
     * @param message The original message that failed
     * @param error The error that occurred
     * @param retryCount Number of retries attempted
     * @return true if successfully published, false otherwise
     */
    fun publishToDlq(
        message: TMessage,
        error: Throwable,
        retryCount: Int = 0,
    ): Boolean

    /**
     * Get the DLQ topic name.
     *
     * @return The DLQ topic name
     */
    fun getDlqTopic(): String
}
