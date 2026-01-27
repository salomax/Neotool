package io.github.salomax.neotool.comms.email.integration

import io.github.salomax.neotool.comms.email.dto.EmailContent
import io.github.salomax.neotool.comms.email.dto.EmailContentKind
import io.github.salomax.neotool.comms.email.events.EmailSendRequestedEvent
import io.github.salomax.neotool.comms.email.events.EmailSendRequestedPayload
import io.github.salomax.neotool.comms.email.kafka.EmailDlqMessage
import io.github.salomax.neotool.comms.email.kafka.EmailTopics
import io.github.salomax.neotool.comms.events.CommsEventType
import io.github.salomax.neotool.common.test.integration.BaseIntegrationTest
import io.github.salomax.neotool.common.test.integration.KafkaIntegrationTest
import io.micronaut.configuration.kafka.annotation.KafkaClient
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
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue

@KafkaClient
interface TestEmailKafkaProducer {
    @Topic(EmailTopics.SEND_REQUESTED)
    fun send(
        @KafkaKey key: String,
        message: EmailSendRequestedEvent,
    )
}

@KafkaListener(
    groupId = "test-email-dlq-consumer-group",
    offsetReset = OffsetReset.EARLIEST,
)
class TestEmailDlqConsumer {
    private val receivedMessages = ConcurrentLinkedQueue<EmailDlqMessage>()

    @Topic(EmailTopics.SEND_DLQ)
    fun receive(
        @KafkaKey key: String,
        message: EmailDlqMessage,
    ) {
        receivedMessages.offer(message)
    }

    fun getReceivedMessages(): List<EmailDlqMessage> = receivedMessages.toList()

    fun clear() {
        receivedMessages.clear()
    }
}

@MicronautTest
@DisplayName("Email Kafka Integration Tests")
@Tag("integration")
class EmailKafkaIntegrationTest : BaseIntegrationTest(), KafkaIntegrationTest {
    @Inject
    lateinit var producer: TestEmailKafkaProducer

    @Inject
    lateinit var dlqConsumer: TestEmailDlqConsumer

    override fun getProperties(): MutableMap<String, String> {
        val props = super.getProperties().toMutableMap()

        props +=
            mapOf(
                "comms.email.provider" to "mock",
                "datasources.default.enabled" to "false",
                "jpa.default.enabled" to "false",
                "flyway.enabled" to "false",
                "kafka.consumers.comms-email-send.group-id" to "comms-email-send-consumer-group-test",
                "kafka.consumers.comms-email-send.enable-auto-commit" to "false",
                "kafka.consumers.comms-email-send.auto-offset-reset" to "earliest",
                "kafka.consumers.comms-email-send.key-deserializer" to StringDeserializer::class.java.name,
                "kafka.consumers.comms-email-send.value-deserializer" to "io.micronaut.serde.kafka.KafkaSerdeDeserializer",
            )

        return props
    }

    @Test
    fun `should send invalid message to DLQ`() {
        dlqConsumer.clear()

        val requestId = "email-dlq-${System.currentTimeMillis()}"
        val event =
            EmailSendRequestedEvent(
                id = requestId,
                type = CommsEventType.EMAIL_SEND_REQUESTED,
                traceId = requestId,
                payload =
                    EmailSendRequestedPayload(
                        to = "user@example.com",
                        content =
                            EmailContent(
                                kind = EmailContentKind.TEMPLATE,
                                templateKey = "welcome",
                                locale = "en-US",
                                variables = mapOf("name" to "User"),
                            ),
                    ),
                createdAt = Instant.now(),
            )

        producer.send(requestId, event)

        val dlqMessage = waitForDlqMessage(requestId, timeoutMs = 20000L)

        assertThat(dlqMessage).isNotNull
        dlqMessage?.let {
            assertThat(it.originalMessage.id).isEqualTo(requestId)
            assertThat(it.errorType).contains("ValidationException")
            assertThat(it.errorMessage).contains("Template content is not supported")
            assertThat(it.retryCount).isEqualTo(0)
            assertThat(it.failedAt).isNotBlank
        }
    }

    @Test
    fun `should process raw email without sending to DLQ`() {
        dlqConsumer.clear()

        val requestId = "email-ok-${System.currentTimeMillis()}"
        val event =
            EmailSendRequestedEvent(
                id = requestId,
                type = CommsEventType.EMAIL_SEND_REQUESTED,
                traceId = requestId,
                payload =
                    EmailSendRequestedPayload(
                        to = "user@example.com",
                        content =
                            EmailContent(
                                kind = EmailContentKind.RAW,
                                subject = "Hello",
                                body = "World",
                            ),
                    ),
                createdAt = Instant.now(),
            )

        producer.send(requestId, event)

        val dlqMessage = waitForDlqMessage(requestId, timeoutMs = 5000L)
        assertThat(dlqMessage).isNull()
    }

    private fun waitForDlqMessage(
        recordId: String,
        timeoutMs: Long = 10000L,
        pollIntervalMs: Long = 100L,
    ): EmailDlqMessage? {
        val startTime = System.currentTimeMillis()
        while ((System.currentTimeMillis() - startTime) < timeoutMs) {
            val dlqMessages = dlqConsumer.getReceivedMessages()
            val dlqMessage = dlqMessages.firstOrNull { it.originalMessage.id == recordId }

            if (dlqMessage != null) {
                return dlqMessage
            }

            Thread.sleep(pollIntervalMs)
        }

        return null
    }
}
