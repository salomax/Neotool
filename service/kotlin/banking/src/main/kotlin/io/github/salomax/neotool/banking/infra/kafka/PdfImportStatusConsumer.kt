package io.github.salomax.neotool.banking.infra.kafka

import io.github.salomax.neotool.banking.domain.pdfimport.ExtractionRoutingService
import io.github.salomax.neotool.banking.domain.pdfimport.PdfImportService
import io.github.salomax.neotool.common.error.CommonErrorCode
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
    private val extractionRoutingService: ExtractionRoutingService =
        ExtractionRoutingService(pdfImportService),
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Topic("\${kafka.topics.pdf.extractor-submitted:pdf-extractor.submitted}")
    fun onExtractorSubmitted(event: Map<String, Any?>) {
        handleEvent("pdf-extractor.submitted", event) { jobId, accountId, _ ->
            pdfImportService.markExtracting(jobId, accountId)
        }
    }

    @Topic("\${kafka.topics.pdf.extractor-extracted:pdf-extractor.extracted}")
    fun onExtractorExtracted(event: Map<String, Any?>) {
        handleEvent("pdf-extractor.extracted", event) { jobId, accountId, callbackContext ->
            extractionRoutingService.routePdfExtraction(
                sourceTopic = "pdf-extractor.extracted",
                jobId = jobId,
                accountId = accountId,
                callbackContext = callbackContext,
                documentType = event["document_type"]?.toString(),
                extractedText = event["extracted_text"] as? String,
                extractionMetadata = event["extraction_metadata"],
            )
        }
    }

    @Topic("\${kafka.topics.pdf.extractor-dlq:pdf-extractor.dlq}")
    fun onExtractorDlq(event: Map<String, Any?>) {
        handleEvent("pdf-extractor.dlq", event) { jobId, accountId, _ ->
            val error = event["error"]?.toString()
            pdfImportService.markFailed(
                jobId = jobId,
                accountId = accountId,
                errorMessage = error,
                errorCode = CommonErrorCode.SERVICE_UNAVAILABLE.code,
                errorDetails =
                    mapOf(
                        "topic" to "pdf-extractor.dlq",
                        "error" to error,
                        "retryable" to false,
                    ),
            )
        }
    }

    @Topic("\${kafka.topics.pdf.banking-reader-done:assistant.banking-pdf-reader.done}")
    fun onBankingReaderDone(event: Map<String, Any?>) {
        handleEvent("assistant.banking-pdf-reader.done", event) { jobId, accountId, _ ->
            val llmOutput = event["llm_output"] as? Map<*, *>
            if (llmOutput == null) {
                val reason = if (event.containsKey("llm_output")) "invalid_llm_output_type" else "missing_llm_output"
                val details =
                    mapOf(
                        "topic" to "assistant.banking-pdf-reader.done",
                        "reason" to reason,
                        "schema_version" to event["schema_version"]?.toString(),
                        "document_type" to event["document_type"]?.toString(),
                    )
                val summary = "assistant_pdf_reader_done_invalid_payload: $reason"
                return@handleEvent pdfImportService.markFailed(
                    jobId = jobId,
                    accountId = accountId,
                    errorMessage = summary,
                    errorCode = CommonErrorCode.INTERNAL_ERROR.code,
                    errorDetails = details,
                )
            }
            pdfImportService.markAwaitingReview(
                jobId = jobId,
                accountId = accountId,
                llmOutput = llmOutput,
            )
        }
    }

    @Topic("\${kafka.topics.pdf.banking-reader-failed:assistant.banking-pdf-reader.failed}")
    fun onBankingReaderFailed(event: Map<String, Any?>) {
        handleEvent("assistant.banking-pdf-reader.failed", event) { jobId, accountId, _ ->
            val error = event["error"] as? Map<*, *>
            val errorMetadata = event["error_metadata"] as? Map<*, *>
            val errorCode = error?.get("code")?.toString()
            val errorMessage = error?.get("message")?.toString()
            val summary =
                buildString {
                    append("assistant_pdf_reader_failed")
                    if (!errorCode.isNullOrBlank()) {
                        append(": ")
                        append(errorCode)
                    }
                    if (!errorMessage.isNullOrBlank()) {
                        append(" - ")
                        append(errorMessage)
                    }
                }
            val details =
                mapOf(
                    "topic" to "assistant.banking-pdf-reader.failed",
                    "schema_version" to event["schema_version"]?.toString(),
                    "document_type" to event["document_type"]?.toString(),
                    "error" to
                        mapOf(
                            "code" to errorCode,
                            "message" to errorMessage,
                            "retryable" to (error?.get("retryable") as? Boolean),
                        ),
                    "error_metadata" to errorMetadata,
                )
            pdfImportService.markFailed(
                jobId = jobId,
                accountId = accountId,
                errorMessage = summary,
                errorCode = errorCode ?: CommonErrorCode.INTERNAL_ERROR.code,
                errorParameters = errorMetadata?.toStringKeyMap(),
                errorDetails = details,
            )
        }
    }

    private fun handleEvent(
        topic: String,
        event: Map<String, Any?>,
        action: (jobId: UUID, accountId: UUID, callbackContext: Map<*, *>) -> Any?,
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

            val updated = action(jobId, accountId, callbackContext)
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

    private fun Map<*, *>.toStringKeyMap(): Map<String, Any?> = entries.associate { it.key.toString() to it.value }
}
