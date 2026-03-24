package io.github.salomax.neotool.banking.infra.kafka

import io.github.salomax.neotool.banking.client.import.messaging.*
import io.github.salomax.neotool.banking.job.import.*
import io.github.salomax.neotool.banking.service.import.parser.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.verify
import java.util.UUID

@DisplayName("AssistantBankingReaderRequestedProducer")
class AssistantBankingReaderRequestedProducerTest {
    private lateinit var client: AssistantBankingReaderRequestedProducerKafkaClient
    private lateinit var producer: AssistantBankingReaderRequestedProducer

    @BeforeEach
    fun setUp() {
        client = org.mockito.kotlin.mock()
        producer = AssistantBankingReaderRequestedProducer(client)
    }

    @Test
    fun `publish sends jobId as key and full event payload`() {
        val jobId = UUID.randomUUID()
        val callback =
            ImportCallbackContext(
                job_id = jobId,
                account_id = UUID.randomUUID(),
                user_id = UUID.randomUUID(),
            )
        val event =
            AssistantBankingReaderRequestedEvent(
                job_id = jobId,
                document_type = "CREDIT_CARD_BILL",
                file_type = "PDF",
                account_type = "CREDIT",
                source_topic = "pdf-extractor.extracted",
                extracted_text = "Page 1 text",
                extraction_metadata =
                    mapOf(
                        "page_count" to 2,
                        "tables_found" to 1,
                        "fingerprint" to
                            mapOf(
                                "status" to "unknown_profile",
                                "reason" to "no_known_pdf_signature_match",
                            ),
                    ),
                callback_context = callback,
            )

        producer.publish(event)

        verify(client).send(jobId.toString(), event)
    }
}
