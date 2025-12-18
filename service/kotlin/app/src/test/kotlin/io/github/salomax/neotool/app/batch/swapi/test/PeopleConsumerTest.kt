package io.github.salomax.neotool.app.batch.swapi.test

import io.github.salomax.neotool.app.batch.swapi.PeopleConsumer
import io.github.salomax.neotool.app.batch.swapi.PeopleDlqPublisher
import io.github.salomax.neotool.app.batch.swapi.PeopleMessage
import io.github.salomax.neotool.app.batch.swapi.PeoplePayload
import io.github.salomax.neotool.app.batch.swapi.PeopleProcessor
import io.github.salomax.neotool.app.batch.swapi.metrics.PeopleMetrics
import io.github.salomax.neotool.common.batch.ConsumerConfig
import io.github.salomax.neotool.common.batch.ProcessingExecutor
import io.github.salomax.neotool.common.batch.exceptions.ProcessingException
import io.github.salomax.neotool.common.batch.exceptions.ValidationException
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@DisplayName("PeopleConsumer Unit Tests")
class PeopleConsumerTest {
    private lateinit var processor: PeopleProcessor
    private lateinit var dlqPublisher: PeopleDlqPublisher
    private lateinit var metrics: PeopleMetrics
    private lateinit var consumer: Consumer<*, *>
    private lateinit var config: ConsumerConfig
    private lateinit var processingExecutor: ProcessingExecutor
    private lateinit var peopleConsumer: PeopleConsumer

    @BeforeEach
    fun setUp() {
        val registry: MeterRegistry = SimpleMeterRegistry()
        metrics = PeopleMetrics(registry)
        processor = PeopleProcessor(metrics)
        dlqPublisher = mock()
        consumer = mock()
        // Use shorter retry delays for faster tests
        config =
            ConsumerConfig(
                maxRetries = 3,
                initialRetryDelayMs = 10L,
                maxRetryDelayMs = 100L,
                retryBackoffMultiplier = 2.0,
                commitTimeoutSeconds = 5L,
                enableDlqFallback = false,
                dlqMaxRetries = 3,
                shutdownTimeoutSeconds = 5L,
            )
        processingExecutor = ProcessingExecutor()
        peopleConsumer = PeopleConsumer(processor, dlqPublisher, metrics, config, processingExecutor)
    }

    @AfterEach
    fun tearDown() {
        processingExecutor.onDestroy()
    }

    @Nested
    @DisplayName("receive() - Success Cases")
    inner class SuccessTests {
        @Test
        fun `should process message and commit offset on success`() {
            // Arrange
            val message = createValidMessage()
            val partition = 0
            val offset = 100L
            val topicPartition = TopicPartition("swapi.people.v1", partition)
            val offsetAndMetadata = OffsetAndMetadata(offset + 1)

            // Act
            peopleConsumer.receive("1", message, consumer, partition, offset)
            Thread.sleep(500) // Allow async processing to complete
            // Trigger commit processing by calling receive again (processPendingCommits is called at start)
            peopleConsumer.receive("dummy", message, consumer, partition, offset + 1)

            // Assert
            verify(consumer, times(1)).commitSync(
                mapOf(topicPartition to offsetAndMetadata),
                Duration.ofSeconds(5),
            )
            verify(dlqPublisher, never()).publishToDlq(any(), any(), any())
        }
    }

    @Nested
    @DisplayName("receive() - Retry Cases")
    inner class RetryTests {
        @Test
        fun `should retry on ProcessingException and eventually succeed`() {
            // Arrange
            val message = createValidMessage()
            val partition = 0
            val offset = 100L

            // First call throws, second succeeds
            var callCount = 0
            val realProcessor = PeopleProcessor(metrics)
            val processorSpy = spy(realProcessor)
            doAnswer { invocation ->
                callCount++
                val msg = invocation.getArgument<PeopleMessage>(0)
                if (callCount == 1) {
                    throw ProcessingException("Temporary failure")
                }
                // Second call succeeds - call real method
                realProcessor.process(msg)
            }.whenever(processorSpy).process(any())

            val consumerWithRetry = PeopleConsumer(processorSpy, dlqPublisher, metrics, config, processingExecutor)

            // Act
            consumerWithRetry.receive("1", message, consumer, partition, offset)
            // Wait for retry delay (initialRetryDelayMs = 1000ms) plus processing time
            Thread.sleep(2000) // Allow async processing and retry to complete
            // Trigger commit processing
            consumerWithRetry.receive("dummy", message, consumer, partition, offset + 1)

            // Assert
            assertThat(callCount).isGreaterThanOrEqualTo(2) // Initial + at least 1 retry
            verify(consumer, times(1)).commitSync(any(), any())
            verify(dlqPublisher, never()).publishToDlq(any(), any(), any())
        }

        @Test
        fun `should send to DLQ after max retries exhausted`() {
            // Arrange
            val message = createValidMessage()
            val message2 = createValidMessage().copy(recordId = "2")
            val partition = 0
            val offset = 100L
            val dlqLatch = CountDownLatch(1)
            // Use shorter retry config for faster test execution
            val testConfig = config.copy(maxRetries = 2, initialRetryDelayMs = 10L, maxRetryDelayMs = 50L)

            // Always throw ProcessingException
            val failingProcessor = mock<PeopleProcessor>()
            doAnswer {
                throw ProcessingException("Persistent failure")
            }.whenever(failingProcessor).process(any())
            whenever(failingProcessor.getRecordId(any())).thenAnswer { (it.arguments[0] as PeopleMessage).recordId }
            val consumerWithFailure =
                PeopleConsumer(
                    failingProcessor,
                    dlqPublisher,
                    metrics,
                    testConfig,
                    processingExecutor,
                )

            whenever(dlqPublisher.publishToDlq(any(), any(), any())).thenAnswer {
                dlqLatch.countDown()
                true
            }

            // Act
            consumerWithFailure.receive("1", message, consumer, partition, offset)

            // Wait for DLQ publish to complete (with retries, this should take ~200ms max)
            dlqLatch.await(5, TimeUnit.SECONDS)

            // Allow time for whenComplete handler to add commit to pendingCommits
            Thread.sleep(100)

            // Trigger commit flush
            consumerWithFailure.receive("dummy", message2, consumer, partition, offset + 1)

            // Wait a bit for commit to be processed
            Thread.sleep(100)

            // Assert - Verify DLQ was called at least once for the failing message
            verify(dlqPublisher, atLeast(1)).publishToDlq(eq(message), any(), any())
            verify(consumer, atLeast(1)).commitSync(any(), any()) // Still commits to prevent reprocessing
        }
    }

    @Nested
    @DisplayName("receive() - Validation Errors")
    inner class ValidationErrorTests {
        @Test
        fun `should send to DLQ immediately on ValidationException without retry`() {
            // Arrange
            val message = createValidMessage()
            val partition = 0
            val offset = 100L

            // Throw ValidationException (should not retry)
            val validationFailingProcessor = mock<PeopleProcessor>()
            doAnswer {
                throw ValidationException("Invalid data")
            }.whenever(validationFailingProcessor).process(any())
            whenever(
                validationFailingProcessor.getRecordId(any()),
            ).thenAnswer { (it.arguments[0] as PeopleMessage).recordId }
            val consumerWithValidationError =
                PeopleConsumer(
                    validationFailingProcessor,
                    dlqPublisher,
                    metrics,
                    config,
                    processingExecutor,
                )

            whenever(dlqPublisher.publishToDlq(any(), any(), any())).thenReturn(true)

            // Act
            consumerWithValidationError.receive("1", message, consumer, partition, offset)
            Thread.sleep(500) // Allow async processing to complete
            // Trigger commit processing
            consumerWithValidationError.receive("dummy", message, consumer, partition, offset + 1)

            // Assert
            verify(dlqPublisher, times(1)).publishToDlq(any(), any(), any())
            verify(consumer, times(1)).commitSync(any(), any())
        }
    }

    private fun createValidMessage(): PeopleMessage {
        return PeopleMessage(
            batchId = "test-batch-id",
            recordId = "1",
            ingestedAt = Instant.now().toString(),
            payload =
                PeoplePayload(
                    name = "Luke Skywalker",
                    height = "172",
                    mass = "77",
                    hairColor = "blond",
                    skinColor = "fair",
                    eyeColor = "blue",
                    birthYear = "19BBY",
                    gender = "male",
                    homeworldUrl = "https://swapi.dev/api/planets/1/",
                    films = listOf("https://swapi.dev/api/films/1/"),
                    species = listOf("https://swapi.dev/api/species/1/"),
                    vehicles = emptyList(),
                    starships = listOf("https://swapi.dev/api/starships/12/"),
                ),
        )
    }
}
