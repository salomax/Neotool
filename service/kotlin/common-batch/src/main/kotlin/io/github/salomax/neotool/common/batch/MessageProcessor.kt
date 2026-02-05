package io.github.salomax.neotool.common.batch

/**
 * Interface for processing batch messages.
 *
 * @param TMessage The message type to process
 */
interface MessageProcessor<TMessage> {
    /**
     * Process a message.
     *
     * @param message The message to process
     * @throws ValidationException if message validation fails (not retried)
     * @throws PermanentProcessingException if processing fails with a permanent error (not retried)
     * @throws ProcessingException if processing fails with a transient error (retried)
     */
    fun process(message: TMessage)

    /**
     * Extract record ID from message for logging and identification.
     *
     * @param message The message
     * @return The record ID
     */
    fun getRecordId(message: TMessage): String
}
