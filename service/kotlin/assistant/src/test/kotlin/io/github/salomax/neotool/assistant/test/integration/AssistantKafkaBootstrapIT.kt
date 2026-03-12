package io.github.salomax.neotool.assistant.test.integration

import io.github.salomax.neotool.assistant.infra.kafka.BankingReaderRequestedBootstrapConsumer
import io.github.salomax.neotool.common.test.integration.BaseIntegrationTest
import io.github.salomax.neotool.common.test.integration.KafkaIntegrationTest
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.errors.TopicExistsException
import org.apache.kafka.common.serialization.StringSerializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.Properties
import java.util.UUID
import java.util.concurrent.TimeUnit

@MicronautTest(startApplication = true)
@DisplayName("Assistant Kafka Bootstrap IT")
@Tag("integration")
@Tag("assistant")
class AssistantKafkaBootstrapIT : BaseIntegrationTest(), KafkaIntegrationTest {
    companion object {
        private const val REQUESTED_TOPIC = "assistant.banking-reader.requested.bootstrap-it"
    }

    @Inject
    lateinit var bootstrapConsumer: BankingReaderRequestedBootstrapConsumer

    override fun getProperties(): MutableMap<String, String> {
        val props = super.getProperties().toMutableMap()
        val bootstrapServers = getKafkaContainer().bootstrapServers
        props["datasources.default.enabled"] = "false"
        props["jpa.default.enabled"] = "false"
        props["flyway.enabled"] = "false"
        props["kafka.enabled"] = "true"
        props["kafka.bootstrap.servers"] = bootstrapServers
        props["assistant.banking-reader.consumer.enabled"] = "false"
        props["assistant.banking-reader.bootstrap-consumer.enabled"] = "true"
        props["kafka.topics.pdf.banking-reader-requested"] = REQUESTED_TOPIC
        props["kafka.consumers.banking-reader.group-id"] = "assistant-kafka-bootstrap-it"
        return props
    }

    @Test
    fun `assistant starts with kafka enabled and subscribes to banking reader requested topic`() {
        ensureTopicExists(REQUESTED_TOPIC)
        bootstrapConsumer.resetCount()

        publishProbeMessage(
            topic = REQUESTED_TOPIC,
            key = UUID.randomUUID().toString(),
            payload = """{"probe":"assistant-kafka-bootstrap"}""",
        )

        val consumed = waitForConsumedMessage(timeoutMs = 10_000L)
        assertThat(consumed).isTrue()
        assertThat(bootstrapConsumer.consumedCount()).isGreaterThanOrEqualTo(1)
    }

    private fun waitForConsumedMessage(
        timeoutMs: Long,
        pollIntervalMs: Long = 100L,
    ): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (bootstrapConsumer.consumedCount() > 0) {
                return true
            }
            Thread.sleep(pollIntervalMs)
        }
        return false
    }

    private fun publishProbeMessage(
        topic: String,
        key: String,
        payload: String,
    ) {
        val bootstrapServers = getKafkaContainer().bootstrapServers
        val producerProps =
            Properties().apply {
                put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
                put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
                put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
            }

        KafkaProducer<String, String>(producerProps).use { producer ->
            producer.send(ProducerRecord(topic, key, payload)).get(5, TimeUnit.SECONDS)
            producer.flush()
        }
    }

    private fun ensureTopicExists(topic: String) {
        val bootstrapServers = getKafkaContainer().bootstrapServers
        val adminProps =
            Properties().apply {
                put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
            }

        AdminClient.create(adminProps).use { admin ->
            try {
                admin
                    .createTopics(listOf(NewTopic(topic, 1, 1)))
                    .all()
                    .get(5, TimeUnit.SECONDS)
            } catch (ex: Exception) {
                val cause = ex.cause
                if (cause !is TopicExistsException) {
                    throw ex
                }
            }
        }
    }
}
