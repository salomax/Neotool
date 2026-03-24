package io.github.salomax.neotool.banking.infra.kafka

import io.github.salomax.neotool.banking.client.import.messaging.*
import io.github.salomax.neotool.banking.job.import.*
import io.github.salomax.neotool.banking.service.import.parser.*
import io.github.salomax.neotool.banking.domain.import.ImportSource
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.verify
import java.util.UUID

@DisplayName("ImportFileProducer")
class ImportFileProducerTest {
    private lateinit var client: ImportFileProducerKafkaClient
    private lateinit var producer: ImportFileProducer

    private val jobId = UUID.randomUUID()
    private val assetId = UUID.randomUUID()
    private val financialAccountId = UUID.randomUUID()
    private val accountId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        client = org.mockito.kotlin.mock()
        producer = ImportFileProducer(client)
    }

    @Test
    fun `publish sends to client with jobId as key and full event`() {
        val event =
            ImportFileRequestedEvent(
                jobId = jobId,
                assetId = assetId,
                source = ImportSource.OFX,
                financialAccountId = financialAccountId,
                accountId = accountId,
            )

        producer.publish(event)

        verify(client).send(jobId.toString(), event)
    }
}
