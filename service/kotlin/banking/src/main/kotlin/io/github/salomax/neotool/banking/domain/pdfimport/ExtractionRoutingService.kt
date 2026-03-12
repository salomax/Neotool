package io.github.salomax.neotool.banking.domain.pdfimport

import io.github.salomax.neotool.banking.domain.import.fingerprint.ExcelFingerprintClassifier
import io.github.salomax.neotool.banking.domain.import.fingerprint.FingerprintClassification
import io.github.salomax.neotool.banking.domain.import.fingerprint.FingerprintClassificationInput
import io.github.salomax.neotool.banking.domain.import.fingerprint.FingerprintClassifierRegistry
import io.github.salomax.neotool.banking.domain.import.fingerprint.FingerprintFileType
import io.github.salomax.neotool.banking.domain.import.fingerprint.PdfFingerprintClassifier
import io.github.salomax.neotool.banking.domain.pdfimport.reader.DeterministicPdfReaderRegistry
import io.github.salomax.neotool.banking.domain.pdfimport.reader.DeterministicPdfReaderRouter
import io.github.salomax.neotool.banking.domain.pdfimport.reader.NubankCreditCardBillPdfReader
import io.github.salomax.neotool.banking.infra.kafka.AssistantBankingReaderRequestedEvent
import io.github.salomax.neotool.banking.infra.kafka.AssistantBankingReaderRequestedProducer
import io.github.salomax.neotool.banking.infra.kafka.PdfImportCallbackContext
import io.micronaut.context.annotation.Property
import jakarta.inject.Singleton
import org.slf4j.LoggerFactory
import java.util.UUID

private const val ASSISTANT_REQUEST_FILE_TYPE = "PDF"

@Singleton
open class ExtractionRoutingService(
    private val pdfImportService: PdfImportService,
    private val fingerprintClassifierRegistry: FingerprintClassifierRegistry =
        FingerprintClassifierRegistry(
            listOf(
                PdfFingerprintClassifier(),
                ExcelFingerprintClassifier(),
            ),
        ),
    private val deterministicPdfReaderRouter: DeterministicPdfReaderRouter =
        DeterministicPdfReaderRouter(
            DeterministicPdfReaderRegistry(
                listOf(
                    NubankCreditCardBillPdfReader(),
                ),
            ),
        ),
    private val assistantRequestProducer: AssistantBankingReaderRequestedProducer? = null,
    @param:Property(name = "banking.pdf-import.deterministic-enabled", defaultValue = "true")
    private val deterministicEnabled: Boolean = true,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Suppress("LongParameterList")
    open fun routePdfExtraction(
        sourceTopic: String,
        jobId: UUID,
        accountId: UUID,
        callbackContext: Map<*, *>,
        documentType: String?,
        extractedText: String?,
        extractionMetadata: Any?,
    ): PdfImportJob? {
        val extracted =
            pdfImportService.markExtracted(
                jobId = jobId,
                accountId = accountId,
                extractedText = extractedText,
                extractionMetadata = extractionMetadata,
            ) ?: return null

        val metadataMap = normalizeMetadata(extractionMetadata)
        val resolvedDocumentType = normalizeDocumentType(documentType, extracted.importType)
        val classification =
            fingerprintClassifierRegistry.classify(
                FingerprintClassificationInput(
                    fileType = FingerprintFileType.PDF,
                    documentType = resolvedDocumentType,
                    extractedText = extractedText,
                    extractionMetadata = metadataMap,
                ),
            )
        if (deterministicEnabled && classification.isKnownProfile()) {
            val profileId = classification.profileId
            if (profileId != null) {
                val deterministicOutput =
                    deterministicPdfReaderRouter.parse(
                        profileId = profileId,
                        documentType = resolvedDocumentType,
                        extractedText = extractedText.orEmpty(),
                        extractionMetadata = metadataMap,
                    )
                if (deterministicOutput == null) {
                    log.warn(
                        "Known PDF profile has no deterministic reader yet: jobId={}, profileId={}",
                        jobId,
                        profileId,
                    )
                } else {
                    log.info(
                        "Deterministic PDF parse ready: jobId={}, profileId={}, readerId={}, records={}",
                        jobId,
                        profileId,
                        deterministicOutput.readerId,
                        deterministicOutput.normalizedRecords.size,
                    )
                    return pdfImportService.markAwaitingReviewFromCanonical(
                        jobId = jobId,
                        accountId = accountId,
                        canonicalOutput = deterministicOutput,
                    )
                }
            }
            log.info(
                "PDF extracted event routed to deterministic path: jobId={}, accountId={}, profileId={}, reason={}",
                jobId,
                accountId,
                classification.profileId,
                classification.reason,
            )
            return extracted
        }
        if (!deterministicEnabled && classification.isKnownProfile()) {
            log.info(
                "Deterministic PDF path disabled by config; routing known profile to LLM: " +
                    "jobId={}, accountId={}, profileId={}, reason={}",
                jobId,
                accountId,
                classification.profileId,
                classification.reason,
            )
        }

        val metadataWithFingerprint = enrichMetadataWithFingerprint(metadataMap, classification)
        publishAssistantRequest(
            sourceTopic = sourceTopic,
            extractedJob = extracted,
            callbackContext = callbackContext,
            documentType = resolvedDocumentType,
            extractedText = extractedText.orEmpty(),
            metadata = metadataWithFingerprint,
        )

        return pdfImportService.markLlmProcessing(jobId, accountId)
    }

    private fun publishAssistantRequest(
        sourceTopic: String,
        extractedJob: PdfImportJob,
        callbackContext: Map<*, *>,
        documentType: String,
        extractedText: String,
        metadata: Map<String, Any?>,
    ) {
        if (assistantRequestProducer == null) {
            log.warn(
                "Assistant request producer unavailable; skipping publish for jobId={}, sourceTopic={}",
                extractedJob.id,
                sourceTopic,
            )
            return
        }

        val callbackJobId = toUuid(callbackContext["job_id"]) ?: requireNotNull(extractedJob.id)
        val callbackAccountId = toUuid(callbackContext["account_id"]) ?: extractedJob.accountId
        val callbackUserId = toUuid(callbackContext["user_id"]) ?: extractedJob.createdByUserId
        assistantRequestProducer.publish(
            AssistantBankingReaderRequestedEvent(
                job_id = requireNotNull(extractedJob.id),
                document_type = documentType,
                file_type = ASSISTANT_REQUEST_FILE_TYPE,
                account_type = accountTypeForDocumentType(documentType),
                source_topic = sourceTopic,
                extracted_text = extractedText,
                extraction_metadata = metadata,
                callback_context =
                    PdfImportCallbackContext(
                        job_id = callbackJobId,
                        account_id = callbackAccountId,
                        user_id = callbackUserId,
                    ),
            ),
        )
    }

    private fun normalizeDocumentType(
        documentType: String?,
        importType: PdfImportType,
    ): String {
        val normalized = documentType?.trim()?.uppercase()
        return if (normalized.isNullOrBlank()) importType.name else normalized
    }

    private fun accountTypeForDocumentType(documentType: String): String =
        when (documentType.trim().uppercase()) {
            PdfImportType.BANK_STATEMENT.name -> "BANK"
            else -> "CREDIT"
        }

    private fun normalizeMetadata(metadata: Any?): Map<String, Any?> =
        when (metadata) {
            null -> emptyMap()
            is Map<*, *> ->
                metadata.entries
                    .filter { it.key != null }
                    .associate { it.key.toString() to it.value }
            else -> mapOf("raw_extraction_metadata" to metadata.toString())
        }

    private fun enrichMetadataWithFingerprint(
        metadata: Map<String, Any?>,
        classification: FingerprintClassification,
    ): Map<String, Any?> =
        metadata +
            mapOf(
                "fingerprint" to
                    mapOf(
                        "status" to classification.status,
                        "reason" to classification.reason,
                        "profile_id" to classification.profileId,
                    ),
            )

    private fun toUuid(value: Any?): UUID? =
        when (value) {
            null -> null
            is UUID -> value
            else -> runCatching { UUID.fromString(value.toString()) }.getOrNull()
        }
}
