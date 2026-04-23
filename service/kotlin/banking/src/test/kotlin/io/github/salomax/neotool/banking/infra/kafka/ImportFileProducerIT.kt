package io.github.salomax.neotool.banking.infra.kafka

import io.github.salomax.neotool.banking.client.import.messaging.*
import io.github.salomax.neotool.banking.job.import.*
import io.github.salomax.neotool.banking.service.import.parser.*
import io.github.salomax.neotool.banking.domain.import.ImportSource
import io.github.salomax.neotool.common.test.integration.BaseIntegrationTest
import io.github.salomax.neotool.common.test.integration.KafkaIntegrationTest
import io.micronaut.configuration.kafka.annotation.KafkaKey
import io.micronaut.configuration.kafka.annotation.KafkaListener
import io.micronaut.configuration.kafka.annotation.OffsetReset
import io.micronaut.configuration.kafka.annotation.Topic
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.apache.kafka.common.serialization.StringDeserializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

@KafkaListener(
    groupId = "banking-import-file-producer-it",
    offsetReset = OffsetReset.EARLIEST,
)
class TestImportFileRequestedConsumer {
    private val received = ConcurrentLinkedQueue<Pair<String, ImportFileRequestedEvent>>()

    @Topic(ImportTopics.FILE_REQUESTED)
    fun receive(
        @KafkaKey key: String,
        event: ImportFileRequestedEvent,
    ) {
        received.offer(key to event)
    }

    fun getReceived(): List<Pair<String, ImportFileRequestedEvent>> = received.toList()

    fun clear() = received.clear()
}

@MicronautTest
@DisplayName("ImportFileProducer IT")
@Tag("integration")
class ImportFileProducerIT : BaseIntegrationTest(), KafkaIntegrationTest {
    override fun getProperties(): MutableMap<String, String> {
        val props = super.getProperties().toMutableMap()
        props["datasources.default.enabled"] = "false"
        props["flyway.enabled"] = "false"
        props["kafka.consumers.import-file.enabled"] = "false"
        props["kafka.consumers.import-status.enabled"] = "false"
        props["kafka.consumers.import-pdf-status.enabled"] = "false"
        props["kafka.consumers.bill-import-status.enabled"] = "false"
        props["kafka.consumers.portfolio-recalculation.enabled"] = "false"
        props["kafka.consumers.market-data-sync.enabled"] = "false"
        props["kafka.consumers.banking-import-file-producer-it.key-deserializer"] =
            StringDeserializer::class.java.name
        props["kafka.consumers.banking-import-file-producer-it.value-deserializer"] =
            "io.micronaut.serde.kafka.KafkaSerdeDeserializer"
        return props
    }

    @Inject
    lateinit var producer: ImportFileProducer

    @Inject
    lateinit var testConsumer: TestImportFileRequestedConsumer

    @Test
    fun `publish sends event to topic consumer receives correct key and payload`() {
        testConsumer.clear()
        val jobId = UUID.randomUUID()
        val assetId = UUID.randomUUID()
        val financialAccountId = UUID.randomUUID()
        val accountId = UUID.randomUUID()
        val event =
            ImportFileRequestedEvent(
                jobId = jobId,
                assetId = assetId,
                source = ImportSource.CSV_NUBANK,
                financialAccountId = financialAccountId,
                accountId = accountId,
            )

        producer.publish(event)

        val received = waitForMessage(jobId.toString(), timeoutMs = 10000L)
        assertThat(received).isNotNull
        received!!
        assertThat(received.first).isEqualTo(jobId.toString())
        assertThat(received.second.jobId).isEqualTo(jobId)
        assertThat(received.second.assetId).isEqualTo(assetId)
        assertThat(received.second.source).isEqualTo(ImportSource.CSV_NUBANK)
        assertThat(received.second.financialAccountId).isEqualTo(financialAccountId)
        assertThat(received.second.accountId).isEqualTo(accountId)
    }

    private fun waitForMessage(
        key: String,
        timeoutMs: Long = 10000L,
        pollIntervalMs: Long = 100L,
    ): Pair<String, ImportFileRequestedEvent>? {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            val match = testConsumer.getReceived().firstOrNull { it.first == key }
            if (match != null) return match
            Thread.sleep(pollIntervalMs)
        }
        return null
    }
}
