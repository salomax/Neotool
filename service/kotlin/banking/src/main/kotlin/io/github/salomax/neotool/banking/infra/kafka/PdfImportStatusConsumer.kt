package io.github.salomax.neotool.banking.infra.kafka

import io.github.salomax.neotool.banking.domain.pdfimport.PdfImportService
import io.micronaut.configuration.kafka.annotation.KafkaListener
import io.micronaut.configuration.kafka.annotation.OffsetReset
import io.micronaut.configuration.kafka.annotation.Topic
import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton
import org.slf4j.LoggerFactory
import java.util.UUID

@Singleton
@Requires(property = "kafka.enabled", value = "true")
@Requires(property = "kafka.consumers.pdf-status.enabled", notEquals = "false", defaultValue = "true")
@KafkaListener(
    groupId = "\${kafka.consumers.pdf-status.group-id:banking-service-pdf-status}",
    offsetReset = OffsetReset.EARLIEST,
)
class PdfImportStatusConsumer(
    private val pdfImportService: PdfImportService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Topic("\${kafka.topics.pdf.extractor-submitted:pdf-extractor.submitted}")
    fun onExtractorSubmitted(event: Map<String, Any?>) {
        handleEvent("pdf-extractor.submitted", event) { jobId, accountId ->
            pdfImportService.markExtracting(jobId, accountId)
        }
    }

    @Topic("\${kafka.topics.pdf.extractor-extracted:pdf-extractor.extracted}")
    fun onExtractorExtracted(event: Map<String, Any?>) {
        handleEvent("pdf-extractor.extracted", event) { jobId, accountId ->
            pdfImportService.markExtractedAndLlmProcessing(
                jobId = jobId,
                accountId = accountId,
                extractedText = event["extracted_text"] as? String,
                extractionMetadata = event["extraction_metadata"],
            )
        }
    }

    @Topic("\${kafka.topics.pdf.extractor-dlq:pdf-extractor.dlq}")
    fun onExtractorDlq(event: Map<String, Any?>) {
        handleEvent("pdf-extractor.dlq", event) { jobId, accountId ->
            val error = event["error"]?.toString()
            pdfImportService.markFailed(jobId, accountId, error)
        }
    }

    @Topic("\${kafka.topics.pdf.banking-reader-done:assistant.banking-pdf-reader.done}")
    fun onBankingReaderDone(event: Map<String, Any?>) {
        handleEvent("assistant.banking-pdf-reader.done", event) { jobId, accountId ->
            pdfImportService.markAwaitingReview(
                jobId = jobId,
                accountId = accountId,
                llmOutput = event["llm_output"],
            )
        }
    }

    private fun handleEvent(
        topic: String,
        event: Map<String, Any?>,
        action: (jobId: UUID, accountId: UUID) -> Any?,
    ) {
        try {
            val callbackContext = event["callback_context"] as? Map<*, *> ?: emptyMap<Any, Any>()
            val jobId = toUuid(callbackContext["job_id"]) ?: toUuid(event["job_id"])
            val accountId = toUuid(callbackContext["account_id"])

            if (jobId == null || accountId == null) {
                log.warn(
                    "Ignoring {} event due to missing callback identifiers: jobId={}, accountId={}",
                    topic,
                    jobId,
                    accountId,
                )
                return
            }

            val updated = action(jobId, accountId)
            if (updated == null) {
                log.warn(
                    "Ignoring {} event for unknown jobId={} accountId={}",
                    topic,
                    jobId,
                    accountId,
                )
            }
        } catch (e: Exception) {
            log.error("Failed handling {} event", topic, e)
        }
    }

    private fun toUuid(value: Any?): UUID? =
        when (value) {
            null -> null
            is UUID -> value
            else -> runCatching { UUID.fromString(value.toString()) }.getOrNull()
        }
}
