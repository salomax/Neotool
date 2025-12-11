package io.github.salomax.neotool.app.batch.swapi

import io.github.salomax.neotool.app.batch.swapi.metrics.PeopleMetrics
import io.github.salomax.neotool.common.batch.MessageProcessor
import io.github.salomax.neotool.common.batch.exceptions.ProcessingException
import io.github.salomax.neotool.common.batch.exceptions.ValidationException
import jakarta.inject.Singleton
import mu.KotlinLogging

/**
 * Business logic processor for SWAPI people data.
 * This is a mocked implementation - no database persistence.
 */
@Singleton
class PeopleProcessor(
    private val metrics: PeopleMetrics,
) : MessageProcessor<PeopleMessage> {
    private val logger = KotlinLogging.logger {}

    /**
     * Process a people message.
     *
     * This is a mocked implementation that:
     * - Validates the message
     * - Logs the processing
     * - Records metrics
     * - Does NOT persist to database
     *
     * @param message The people message to process
     * @throws ValidationException if validation fails (not retried)
     * @throws PermanentProcessingException if processing fails with a permanent error (not retried)
     * @throws ProcessingException if processing fails with a transient error (retried)
     */
    override fun process(message: PeopleMessage) {
        val startTime = System.currentTimeMillis()

        try {
            logger.info {
                "Processing people record: batchId=${message.batchId}, " +
                    "recordId=${message.recordId}, name=${message.payload.name}"
            }

            // Validate message
            validate(message)

            // Mock processing - in real implementation, this would:
            // - Save to database
            // - Update related entities
            // - Trigger downstream processes
            // - etc.

            logger.debug {
                "Mock processing completed for recordId=${message.recordId}: " +
                    "name=${message.payload.name}, " +
                    "height=${message.payload.height}, " +
                    "mass=${message.payload.mass}"
            }

            // Record metrics
            val duration = System.currentTimeMillis() - startTime
            metrics.recordProcessingDuration(duration.toDouble())
            metrics.incrementProcessed()

            logger.info {
                "Successfully processed people record: recordId=${message.recordId} " +
                    "(took ${duration}ms)"
            }
        } catch (e: ValidationException) {
            // ValidationException is not converted - relançada diretamente
            // Consumer é responsável por métricas de erro de alto nível
            val duration = System.currentTimeMillis() - startTime
            metrics.recordProcessingDuration(duration.toDouble())
            logger.error(e) {
                "Validation failed for recordId=${message.recordId}: ${e.message}"
            }
            throw e // Relançar ValidationException diretamente, não converter
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            metrics.recordProcessingDuration(duration.toDouble())
            logger.error(e) {
                "Processing failed for recordId=${message.recordId}: ${e.message}"
            }
            throw ProcessingException("Processing failed: ${e.message}", e)
        }
    }

    /**
     * Extract record ID from message.
     */
    override fun getRecordId(message: PeopleMessage): String {
        return message.recordId
    }

    /**
     * Validate the message structure and content.
     */
    private fun validate(message: PeopleMessage) {
        if (message.batchId.isBlank()) {
            throw ValidationException("batchId cannot be blank")
        }

        if (message.recordId.isBlank()) {
            throw ValidationException("recordId cannot be blank")
        }

        if (message.payload.name.isBlank()) {
            throw ValidationException("payload.name cannot be blank")
        }

        // Validate ingested_at is valid ISO 8601
        try {
            message.getIngestedAtInstant()
        } catch (e: Exception) {
            throw ValidationException("ingested_at is not a valid ISO 8601 timestamp: ${message.ingestedAt}")
        }

        // Additional validation can be added here
    }
}
