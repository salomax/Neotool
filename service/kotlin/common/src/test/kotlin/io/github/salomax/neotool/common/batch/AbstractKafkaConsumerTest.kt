package io.github.salomax.neotool.common.batch

import io.github.salomax.neotool.common.batch.exceptions.PermanentProcessingException
import io.github.salomax.neotool.common.batch.exceptions.ProcessingException
import io.github.salomax.neotool.common.batch.exceptions.ValidationException
import org.apache.kafka.clients.consumer.CommitFailedException
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.WakeupException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.atMost
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Test data class for AbstractKafkaConsumer tests.
 */
data class TestMessage(val recordId: String, val data: String)

/**
 * Concrete test implementation of AbstractKafkaConsumer.
 */
class TestKafkaConsumer(
    processor: MessageProcessor<TestMessage>,
    dlqPublisher: DlqPublisherService<TestMessage>,
    metrics: ConsumerMetrics,
    config: ConsumerConfig,
    processingExecutor: ProcessingExecutor,
) : AbstractKafkaConsumer<
        TestMessage,
        MessageProcessor<TestMessage>,
        DlqPublisherService<TestMessage>,
        ConsumerMetrics,
    >(
        processor,
        dlqPublisher,
        metrics,
        config,
        processingExecutor,
    ) {
    override fun getTopicName(): String = "test.topic.v1"

    // Expose protected method for testing
    fun testHandleDlqFallback(
        message: TestMessage,
        error: Throwable,
        retryCount: Int,
        recordId: String,
    ): Boolean {
        return handleDlqFallback(message, error, retryCount, recordId)
    }
}

/**
 * Test implementation with overridden handleDlqFallback for fallback testing.
 */
class TestKafkaConsumerWithFallback(
    processor: MessageProcessor<TestMessage>,
    dlqPublisher: DlqPublisherService<TestMessage>,
    metrics: ConsumerMetrics,
    config: ConsumerConfig,
    processingExecutor: ProcessingExecutor,
    private val fallbackResult: Boolean,
) : AbstractKafkaConsumer<
        TestMessage,
        MessageProcessor<TestMessage>,
        DlqPublisherService<TestMessage>,
        ConsumerMetrics,
    >(
        processor,
        dlqPublisher,
        metrics,
        config,
        processingExecutor,
    ) {
    override fun getTopicName(): String = "test.topic.v1"

    override fun handleDlqFallback(
        message: TestMessage,
        error: Throwable,
        retryCount: Int,
        recordId: String,
    ): Boolean {
        return fallbackResult
    }
}

@DisplayName("AbstractKafkaConsumer Unit Tests")
class AbstractKafkaConsumerTest {
    private lateinit var processor: MessageProcessor<TestMessage>
    private lateinit var dlqPublisher: DlqPublisherService<TestMessage>
    private lateinit var metrics: ConsumerMetrics
    private lateinit var consumer: Consumer<*, *>
    private lateinit var config: ConsumerConfig
    private lateinit var processingExecutor: ProcessingExecutor
    private lateinit var testConsumer: TestKafkaConsumer

    @BeforeEach
    fun setUp() {
        processor = mock()
        dlqPublisher = mock()
        metrics = mock()
        consumer = mock()
        config =
            ConsumerConfig(
                maxRetries = 3,
                // Short delays for testing
                initialRetryDelayMs = 10L,
                maxRetryDelayMs = 100L,
                retryBackoffMultiplier = 2.0,
                commitTimeoutSeconds = 5L,
                enableDlqFallback = false,
                dlqMaxRetries = 3,
                shutdownTimeoutSeconds = 5L,
            )
        processingExecutor = ProcessingExecutor()
        testConsumer = TestKafkaConsumer(processor, dlqPublisher, metrics, config, processingExecutor)

        // Default mocks
        whenever(processor.getRecordId(any())).thenAnswer { (it.arguments[0] as TestMessage).recordId }
        // commitSync is void, so we don't need to stub it - it will work as-is
        whenever(consumer.assignment()).thenReturn(emptySet())
    }

    private fun createTestMessage(recordId: String = "test-1"): TestMessage {
        return TestMessage(recordId = recordId, data = "test-data")
    }

    @AfterEach
    fun tearDown() {
        // Cleanup: shutdown processing executor to prevent resource leaks
        try {
            processingExecutor.onDestroy()
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }

    /**
     * Wait for a CompletableFuture to complete with timeout.
     */
    private fun waitForCompletion(
        future: CompletableFuture<*>,
        timeoutSeconds: Long = 5,
    ) {
        future.get(timeoutSeconds, TimeUnit.SECONDS)
    }

    /**
     * Wait for async processing to complete by polling for a condition.
     * More reliable than Thread.sleep().
     */
    private fun waitForCondition(
        condition: () -> Boolean,
        timeoutMs: Long = 2000,
        pollIntervalMs: Long = 50,
    ) {
        val startTime = System.currentTimeMillis()
        while (!condition() && (System.currentTimeMillis() - startTime) < timeoutMs) {
            Thread.sleep(pollIntervalMs)
        }
        if (!condition()) {
            throw AssertionError("Condition not met within ${timeoutMs}ms")
        }
    }

    /**
     * Wait for a specific number of verifications to occur.
     */
    private fun waitForVerification(
        verification: () -> Unit,
        timeoutMs: Long = 2000,
        pollIntervalMs: Long = 50,
    ) {
        val startTime = System.currentTimeMillis()
        var lastException: Throwable? = null
        while ((System.currentTimeMillis() - startTime) < timeoutMs) {
            try {
                verification()
                return // Success
            } catch (e: AssertionError) {
                lastException = e
                Thread.sleep(pollIntervalMs)
            }
        }
        throw lastException ?: AssertionError("Verification failed within ${timeoutMs}ms")
    }

    @Nested
    @DisplayName("Success Path Tests")
    inner class SuccessPathTests {
        @Test
        fun `should enqueue commit after successful processing`() {
            // Arrange
            val message = createTestMessage()
            val partition = 0
            val offset = 100L
            val processingLatch = CountDownLatch(1)

            doAnswer {
                processingLatch.countDown()
            }.whenever(processor).process(any())

            // Act
            testConsumer.receive("key", message, consumer, partition, offset)

            // Wait for async processing to complete
            processingLatch.await(2, TimeUnit.SECONDS)

            // Allow time for whenComplete handler to add commit to pendingCommits
            Thread.sleep(100)

            // Trigger commit flush by calling receive again
            testConsumer.receive("key2", createTestMessage("test-2"), consumer, partition, offset + 1)

            // Wait for commit to be processed
            waitForVerification({
                val topicPartition = TopicPartition("test.topic.v1", partition)
                val expectedOffset = OffsetAndMetadata(offset + 1)
                verify(consumer).commitSync(
                    mapOf(topicPartition to expectedOffset),
                    Duration.ofSeconds(5),
                )
            })

            // Assert
            verify(dlqPublisher, never()).publishToDlq(any(), any(), any())
            verify(metrics, never()).incrementError(any())
            verify(metrics, never()).incrementDlq()
        }

        @Test
        fun `should flush pending commits on subsequent receive`() {
            // Arrange
            val message1 = createTestMessage("test-1")
            val message2 = createTestMessage("test-2")
            val partition = 0
            val offset1 = 100L
            val offset2 = 101L
            val processingLatch = CountDownLatch(2)

            doAnswer {
                processingLatch.countDown()
            }.whenever(processor).process(any())

            // Act - Process first message
            testConsumer.receive("key1", message1, consumer, partition, offset1)

            // Process second message (should flush first commit)
            testConsumer.receive("key2", message2, consumer, partition, offset2)

            // Wait for both to process
            processingLatch.await(2, TimeUnit.SECONDS)

            // Allow time for whenComplete handlers to add commits to pendingCommits
            Thread.sleep(100)

            // Process third message (should flush second commit)
            testConsumer.receive("key3", createTestMessage("test-3"), consumer, partition, offset2 + 1)

            // Wait for commits to be processed
            waitForVerification({
                val topicPartition = TopicPartition("test.topic.v1", partition)
                verify(consumer).commitSync(
                    mapOf(topicPartition to OffsetAndMetadata(offset1 + 1)),
                    Duration.ofSeconds(5),
                )
                verify(consumer).commitSync(
                    mapOf(topicPartition to OffsetAndMetadata(offset2 + 1)),
                    Duration.ofSeconds(5),
                )
            })
        }
    }

    @Nested
    @DisplayName("Retry Logic Tests")
    inner class RetryLogicTests {
        @Test
        fun `should retry on ProcessingException and eventually succeed`() {
            // Arrange
            val message = createTestMessage()
            val partition = 0
            val offset = 100L
            val callCount = AtomicInteger(0)
            val successLatch = CountDownLatch(1)
            val message2 = createTestMessage("test-2")

            doAnswer {
                val count = callCount.incrementAndGet()
                val msg = it.arguments[0] as TestMessage
                if (msg.recordId == message.recordId) {
                    if (count <= 2) {
                        throw ProcessingException("Temporary failure")
                    }
                    // Third call succeeds
                    successLatch.countDown()
                }
                // Second message should succeed immediately
            }.whenever(processor).process(any())

            // Act
            testConsumer.receive("key", message, consumer, partition, offset)

            // Wait for successful processing (with retries)
            successLatch.await(5, TimeUnit.SECONDS)

            // Allow time for whenComplete handler to add commit to pendingCommits
            Thread.sleep(100)

            // Trigger commit flush
            testConsumer.receive("key2", message2, consumer, partition, offset + 1)

            // Wait for commit
            waitForVerification({
                verify(consumer).commitSync(any(), any())
            })

            // Assert - Only count calls for the first message
            assertThat(
                callCount.get(),
            ).isGreaterThanOrEqualTo(3) // Initial + 2 retries (may have extra calls from second message)
            // Verify retries were called for the first message (at least 2 times)
            verify(metrics, atLeast(2)).incrementRetry()
            verify(dlqPublisher, never()).publishToDlq(eq(message), any(), any())
        }

        @Test
        fun `should exhaust retries and send to DLQ`() {
            // Arrange
            val message = createTestMessage()
            val message2 = createTestMessage("test-2")
            val partition = 0
            val offset = 100L
            val testConfig = config.copy(maxRetries = 2)
            val consumerWithConfig = TestKafkaConsumer(processor, dlqPublisher, metrics, testConfig, processingExecutor)
            val dlqLatch = CountDownLatch(1)

            doAnswer {
                val msg = it.arguments[0] as TestMessage
                if (msg.recordId == message.recordId) {
                    throw ProcessingException("Persistent failure")
                }
                // Second message should succeed
            }.whenever(processor).process(any())
            whenever(dlqPublisher.publishToDlq(any(), any(), any())).thenAnswer {
                dlqLatch.countDown()
                true
            }

            // Act
            consumerWithConfig.receive("key", message, consumer, partition, offset)

            // Wait for DLQ publish to complete
            dlqLatch.await(5, TimeUnit.SECONDS)

            // Allow time for whenComplete handler to add commit to pendingCommits
            Thread.sleep(100)

            // Trigger commit flush
            consumerWithConfig.receive("key2", message2, consumer, partition, offset + 1)

            // Wait for commit
            waitForVerification({
                verify(consumer).commitSync(any(), any())
            })

            // Assert - Verify calls for first message only (at least 3: initial + 2 retries)
            verify(processor, atLeast(3)).process(eq(message)) // Initial + 2 retries
            verify(metrics, times(2)).incrementRetry()
            verify(metrics).incrementError("processing")
            verify(dlqPublisher).publishToDlq(eq(message), any(), eq(2))
        }

        @Test
        fun `should handle generic Exception in retry loop`() {
            // Arrange
            val message = createTestMessage()
            val message2 = createTestMessage("test-2")
            val partition = 0
            val offset = 100L
            val callCount = AtomicInteger(0)
            val dlqLatch = CountDownLatch(1)

            doAnswer {
                val msg = it.arguments[0] as TestMessage
                if (msg.recordId == message.recordId) {
                    callCount.incrementAndGet()
                    throw RuntimeException("Generic error")
                }
                // Second message should succeed
            }.whenever(processor).process(any())

            val testConfig = config.copy(maxRetries = 1)
            val consumerWithConfig = TestKafkaConsumer(processor, dlqPublisher, metrics, testConfig, processingExecutor)
            whenever(dlqPublisher.publishToDlq(any(), any(), any())).thenAnswer {
                dlqLatch.countDown()
                true
            }

            // Act
            consumerWithConfig.receive("key", message, consumer, partition, offset)

            // Wait for DLQ publish
            dlqLatch.await(5, TimeUnit.SECONDS)

            // Allow time for whenComplete handler to add commit to pendingCommits
            Thread.sleep(100)

            // Trigger commit flush
            consumerWithConfig.receive("key2", message2, consumer, partition, offset + 1)

            // Wait for commit
            waitForVerification({
                verify(consumer).commitSync(any(), any())
            })

            // Assert - Verify calls for first message only (at least 2: initial + 1 retry)
            assertThat(callCount.get()).isGreaterThanOrEqualTo(2) // Initial + 1 retry
            verify(processor, atLeast(2)).process(eq(message)) // Initial + 1 retry
            verify(metrics).incrementRetry()
            verify(metrics).incrementError("processing")
            verify(dlqPublisher).publishToDlq(eq(message), any(), eq(1))
        }

        @Test
        fun `should calculate retry delay with exponential backoff`() {
            // Arrange
            val message = createTestMessage()
            val partition = 0
            val offset = 100L
            val testConfig =
                config.copy(
                    maxRetries = 3,
                    initialRetryDelayMs = 50L,
                    retryBackoffMultiplier = 2.0,
                    maxRetryDelayMs = 500L,
                )
            val consumerWithConfig = TestKafkaConsumer(processor, dlqPublisher, metrics, testConfig, processingExecutor)
            val delays = mutableListOf<Long>()
            val startTimes = mutableListOf<Long>()
            val allAttemptsLatch = CountDownLatch(1)

            doAnswer {
                startTimes.add(System.currentTimeMillis())
                if (startTimes.size > 1) {
                    synchronized(delays) {
                        delays.add(startTimes.last() - startTimes[startTimes.size - 2])
                    }
                }
                if (startTimes.size >= 4) { // Initial + 3 retries
                    allAttemptsLatch.countDown()
                }
                throw ProcessingException("Retry test")
            }.whenever(processor).process(any())
            whenever(dlqPublisher.publishToDlq(any(), any(), any())).thenReturn(true)

            // Act
            consumerWithConfig.receive("key", message, consumer, partition, offset)

            // Wait for all retries to complete
            allAttemptsLatch.await(5, TimeUnit.SECONDS)

            // Assert - Verify delays are approximately exponential (with some tolerance)
            // attempt 1: ~50ms, attempt 2: ~100ms, attempt 3: ~200ms
            assertThat(delays.size).isGreaterThanOrEqualTo(2)
            // First delay should be around initialRetryDelayMs
            assertThat(delays[0]).isBetween(40L, 100L)
            // Second delay should be approximately double
            if (delays.size > 1) {
                assertThat(delays[1]).isBetween(80L, 200L)
            }
        }
    }

    @Nested
    @DisplayName("Validation Error Tests")
    inner class ValidationErrorTests {
        @Test
        fun `should skip retries and send ValidationException to DLQ immediately`() {
            // Arrange
            val message = createTestMessage()
            val message2 = createTestMessage("test-2")
            val partition = 0
            val offset = 100L
            val dlqLatch = CountDownLatch(1)

            doAnswer {
                val msg = it.arguments[0] as TestMessage
                if (msg.recordId == message.recordId) {
                    throw ValidationException("Invalid data")
                }
                // Second message should succeed
            }.whenever(processor).process(any())
            whenever(dlqPublisher.publishToDlq(any(), any(), any())).thenAnswer {
                dlqLatch.countDown()
                true
            }

            // Act
            testConsumer.receive("key", message, consumer, partition, offset)

            // Wait for DLQ publish
            dlqLatch.await(2, TimeUnit.SECONDS)

            // Allow time for whenComplete handler to add commit to pendingCommits
            Thread.sleep(100)

            // Trigger commit flush
            testConsumer.receive("key2", message2, consumer, partition, offset + 1)

            // Wait for commit
            waitForVerification({
                verify(consumer).commitSync(any(), any())
            })

            // Assert - Verify only one call for the first message (no retries)
            verify(processor, times(1)).process(eq(message)) // Only one call, no retries
            verify(metrics).incrementError("validation")
            verify(metrics, never()).incrementRetry()
            val errorCaptor = argumentCaptor<Throwable>()
            verify(dlqPublisher).publishToDlq(eq(message), errorCaptor.capture(), eq(0))
            assertThat(errorCaptor.firstValue).isInstanceOf(ValidationException::class.java)
        }

        @Test
        fun `should skip retries and send PermanentProcessingException to DLQ immediately`() {
            // Arrange
            val message = createTestMessage()
            val message2 = createTestMessage("test-2")
            val partition = 0
            val offset = 100L
            val dlqLatch = CountDownLatch(1)

            doAnswer {
                val msg = it.arguments[0] as TestMessage
                if (msg.recordId == message.recordId) {
                    throw PermanentProcessingException("Invalid payload structure")
                }
                // Second message should succeed
            }.whenever(processor).process(any())
            whenever(dlqPublisher.publishToDlq(any(), any(), any())).thenAnswer {
                dlqLatch.countDown()
                true
            }

            // Act
            testConsumer.receive("key", message, consumer, partition, offset)

            // Wait for DLQ publish
            dlqLatch.await(2, TimeUnit.SECONDS)

            // Allow time for whenComplete handler to add commit to pendingCommits
            Thread.sleep(100)

            // Trigger commit flush
            testConsumer.receive("key2", message2, consumer, partition, offset + 1)

            // Wait for commit
            waitForVerification({
                verify(consumer).commitSync(any(), any())
            })

            // Assert - Verify only one call for the first message (no retries)
            verify(processor, times(1)).process(eq(message)) // Only one call, no retries
            verify(metrics).incrementError("processing_permanent")
            verify(metrics, never()).incrementRetry()
            val errorCaptor = argumentCaptor<Throwable>()
            verify(dlqPublisher).publishToDlq(eq(message), errorCaptor.capture(), eq(0))
            assertThat(errorCaptor.firstValue).isInstanceOf(PermanentProcessingException::class.java)
        }
    }

    @Nested
    @DisplayName("DLQ Publishing Tests")
    inner class DlqPublishingTests {
        @Test
        fun `should pass correct retryCount to DLQ publisher`() {
            // Arrange
            val message = createTestMessage()
            val message2 = createTestMessage("test-2")
            val partition = 0
            val offset = 100L
            val testConfig = config.copy(maxRetries = 2)
            val consumerWithConfig = TestKafkaConsumer(processor, dlqPublisher, metrics, testConfig, processingExecutor)
            val dlqLatch = CountDownLatch(1)
            val retryCountCaptor = argumentCaptor<Int>()

            doAnswer {
                val msg = it.arguments[0] as TestMessage
                if (msg.recordId == message.recordId) {
                    throw ProcessingException("Persistent failure")
                }
                // Second message should succeed
            }.whenever(processor).process(any())
            whenever(dlqPublisher.publishToDlq(any(), any(), retryCountCaptor.capture())).thenAnswer {
                dlqLatch.countDown()
                true
            }

            // Act
            consumerWithConfig.receive("key", message, consumer, partition, offset)

            // Wait for DLQ publish
            dlqLatch.await(5, TimeUnit.SECONDS)

            // Allow time for whenComplete handler to add commit to pendingCommits
            Thread.sleep(100)

            // Trigger commit flush
            consumerWithConfig.receive("key2", message2, consumer, partition, offset + 1)

            // Wait for commit
            waitForVerification({
                verify(consumer).commitSync(any(), any())
            })

            // Assert - retryCount should be equal to maxRetries (2) after all retries exhausted
            verify(dlqPublisher).publishToDlq(eq(message), any(), any())
            assertThat(retryCountCaptor.firstValue).isEqualTo(2) // Should be maxRetries value
        }

        @Test
        fun `should retry DLQ publishing on failure`() {
            // Arrange
            val message = createTestMessage()
            val partition = 0
            val offset = 100L
            val testConfig = config.copy(dlqMaxRetries = 3)
            val consumerWithConfig = TestKafkaConsumer(processor, dlqPublisher, metrics, testConfig, processingExecutor)
            val dlqCallCount = AtomicInteger(0)
            val dlqSuccessLatch = CountDownLatch(1)

            doAnswer { throw ProcessingException("Processing failed") }.whenever(processor).process(any())
            whenever(dlqPublisher.publishToDlq(any(), any(), any())).thenAnswer {
                val count = dlqCallCount.incrementAndGet()
                val success = count >= 3 // Succeed on 3rd attempt
                if (success) {
                    dlqSuccessLatch.countDown()
                }
                success
            }

            // Act
            consumerWithConfig.receive("key", message, consumer, partition, offset)

            // Wait for DLQ to succeed
            dlqSuccessLatch.await(5, TimeUnit.SECONDS)

            // Allow time for whenComplete handler to add commit to pendingCommits
            Thread.sleep(100)

            // Trigger commit flush
            consumerWithConfig.receive("key2", createTestMessage("test-2"), consumer, partition, offset + 1)

            // Wait for commit
            waitForVerification({
                verify(consumer).commitSync(any(), any())
            })

            // Assert
            verify(dlqPublisher, times(3)).publishToDlq(any(), any(), any())
            verify(metrics, times(2)).incrementDlqPublishFailure()
            verify(metrics).incrementDlq()
        }

        @Test
        fun `should return false when DLQ publishing fails completely`() {
            // Arrange
            val message = createTestMessage()
            val partition = 0
            val offset = 100L
            val testConfig = config.copy(maxRetries = 1, dlqMaxRetries = 2)
            val consumerWithConfig = TestKafkaConsumer(processor, dlqPublisher, metrics, testConfig, processingExecutor)
            val dlqAttempts = AtomicInteger(0)
            val allDlqAttemptsLatch = CountDownLatch(1)

            doAnswer { throw ProcessingException("Processing failed") }.whenever(processor).process(any())
            whenever(dlqPublisher.publishToDlq(any(), any(), any())).thenAnswer {
                val attempts = dlqAttempts.incrementAndGet()
                if (attempts >= 3) { // All attempts completed
                    allDlqAttemptsLatch.countDown()
                }
                false
            }

            // Act
            consumerWithConfig.receive("key", message, consumer, partition, offset)

            // Wait for all DLQ attempts to complete
            allDlqAttemptsLatch.await(5, TimeUnit.SECONDS)

            // Allow time for whenComplete handler to run
            Thread.sleep(100)

            // Reset mock so second message succeeds (just to trigger commit flush)
            doAnswer { }.whenever(processor).process(any())

            // Trigger commit flush - should not have any commits
            consumerWithConfig.receive("key2", createTestMessage("test-2"), consumer, partition, offset + 1)

            // Give a moment for any potential commit attempts
            Thread.sleep(200)

            // Assert
            verify(dlqPublisher, times(3)).publishToDlq(any(), any(), any()) // Initial + 2 retries
            verify(metrics, times(3)).incrementDlqPublishFailure()
            verify(consumer, never()).commitSync(any(), any()) // No commit because DLQ failed
        }

        @Test
        fun `should handle DLQ publish exception and retry`() {
            // Arrange
            val message = createTestMessage()
            val partition = 0
            val offset = 100L
            val testConfig = config.copy(dlqMaxRetries = 3)
            val consumerWithConfig = TestKafkaConsumer(processor, dlqPublisher, metrics, testConfig, processingExecutor)
            val dlqCallCount = AtomicInteger(0)
            val dlqSuccessLatch = CountDownLatch(1)

            doAnswer { throw ProcessingException("Processing failed") }.whenever(processor).process(any())
            whenever(dlqPublisher.publishToDlq(any(), any(), any())).thenAnswer {
                val count = dlqCallCount.incrementAndGet()
                if (count <= 2) {
                    throw RuntimeException("DLQ publish error")
                }
                dlqSuccessLatch.countDown()
                true // Succeed on 3rd attempt
            }

            // Act
            consumerWithConfig.receive("key", message, consumer, partition, offset)

            // Wait for DLQ to succeed
            dlqSuccessLatch.await(5, TimeUnit.SECONDS)

            // Allow time for whenComplete handler to add commit to pendingCommits
            Thread.sleep(100)

            // Trigger commit flush
            consumerWithConfig.receive("key2", createTestMessage("test-2"), consumer, partition, offset + 1)

            // Wait for commit
            waitForVerification({
                verify(consumer).commitSync(any(), any())
            })

            // Assert
            verify(dlqPublisher, times(3)).publishToDlq(any(), any(), any())
            verify(metrics, times(2)).incrementDlqPublishFailure()
            verify(metrics).incrementDlq()
        }

        @Test
        fun `should use DLQ fallback when enabled and primary DLQ fails`() {
            // Arrange
            val message = createTestMessage()
            val partition = 0
            val offset = 100L
            val testConfig = config.copy(enableDlqFallback = true, dlqMaxRetries = 1)
            val consumerWithFallback =
                TestKafkaConsumerWithFallback(
                    processor,
                    dlqPublisher,
                    metrics,
                    testConfig,
                    processingExecutor,
                    fallbackResult = true,
                )
            val dlqAttempts = AtomicInteger(0)
            val allDlqAttemptsLatch = CountDownLatch(1)

            doAnswer { throw ProcessingException("Processing failed") }.whenever(processor).process(any())
            whenever(dlqPublisher.publishToDlq(any(), any(), any())).thenAnswer {
                val attempts = dlqAttempts.incrementAndGet()
                if (attempts >= 2) { // Initial + 1 retry
                    allDlqAttemptsLatch.countDown()
                }
                false
            }

            // Act
            consumerWithFallback.receive("key", message, consumer, partition, offset)

            // Wait for all DLQ attempts and fallback
            allDlqAttemptsLatch.await(5, TimeUnit.SECONDS)
            Thread.sleep(100) // Brief wait for fallback

            // Allow time for whenComplete handler to add commit to pendingCommits
            Thread.sleep(100)

            // Trigger commit flush
            consumerWithFallback.receive("key2", createTestMessage("test-2"), consumer, partition, offset + 1)

            // Wait for commit
            waitForVerification({
                verify(consumer).commitSync(any(), any()) // Commit after fallback success
            })

            // Assert
            verify(dlqPublisher, times(2)).publishToDlq(any(), any(), any()) // Initial + 1 retry
        }

        @Test
        fun `should call default handleDlqFallback when enabled and DLQ fails completely`() {
            // Arrange
            val message = createTestMessage()
            val partition = 0
            val offset = 100L
            val testConfig = config.copy(enableDlqFallback = true, dlqMaxRetries = 1)
            val consumerWithConfig = TestKafkaConsumer(processor, dlqPublisher, metrics, testConfig, processingExecutor)
            val dlqAttempts = AtomicInteger(0)
            val allDlqAttemptsLatch = CountDownLatch(1)

            doAnswer { throw ProcessingException("Processing failed") }.whenever(processor).process(any())
            whenever(dlqPublisher.publishToDlq(any(), any(), any())).thenAnswer {
                val attempts = dlqAttempts.incrementAndGet()
                if (attempts >= 2) { // Initial + 1 retry
                    allDlqAttemptsLatch.countDown()
                }
                false
            }

            // Act
            consumerWithConfig.receive("key", message, consumer, partition, offset)

            // Wait for all DLQ attempts and fallback
            allDlqAttemptsLatch.await(5, TimeUnit.SECONDS)
            Thread.sleep(200) // Wait for fallback to be called

            // Assert - Default fallback should be called (returns false)
            verify(dlqPublisher, times(2)).publishToDlq(any(), any(), any()) // Initial + 1 retry
            // Default fallback returns false, so no commit should happen
            verify(consumer, never()).commitSync(any(), any())
        }

        @Test
        fun `should handle DLQ exception and use fallback when enabled`() {
            // Arrange
            val message = createTestMessage()
            val partition = 0
            val offset = 100L
            val testConfig = config.copy(enableDlqFallback = true, dlqMaxRetries = 1)
            val consumerWithFallback =
                TestKafkaConsumerWithFallback(
                    processor,
                    dlqPublisher,
                    metrics,
                    testConfig,
                    processingExecutor,
                    fallbackResult = true,
                )
            val dlqExceptionLatch = CountDownLatch(1)

            doAnswer { throw ProcessingException("Processing failed") }.whenever(processor).process(any())
            var dlqCallCount = 0
            whenever(dlqPublisher.publishToDlq(any(), any(), any())).thenAnswer {
                dlqCallCount++
                if (dlqCallCount >= 2) { // After first attempt + 1 retry
                    dlqExceptionLatch.countDown()
                }
                throw RuntimeException("DLQ publish exception") // Throw exception to test exception path
            }

            // Act
            consumerWithFallback.receive("key", message, consumer, partition, offset)

            // Wait for DLQ exceptions and fallback
            dlqExceptionLatch.await(5, TimeUnit.SECONDS)
            Thread.sleep(200) // Wait for fallback

            // Assert - Should have attempted DLQ, then used fallback
            verify(dlqPublisher, atLeast(2)).publishToDlq(any(), any(), any())
        }

        @Test
        fun `should handle DLQ exception without fallback when disabled`() {
            // Arrange
            val message = createTestMessage()
            val partition = 0
            val offset = 100L
            val testConfig = config.copy(enableDlqFallback = false, dlqMaxRetries = 1)
            val consumerWithConfig = TestKafkaConsumer(processor, dlqPublisher, metrics, testConfig, processingExecutor)
            val dlqExceptionLatch = CountDownLatch(1)

            doAnswer { throw ProcessingException("Processing failed") }.whenever(processor).process(any())
            var dlqCallCount = 0
            whenever(dlqPublisher.publishToDlq(any(), any(), any())).thenAnswer {
                dlqCallCount++
                if (dlqCallCount >= 2) { // After first attempt + 1 retry
                    dlqExceptionLatch.countDown()
                }
                throw RuntimeException("DLQ publish exception")
            }

            // Act
            consumerWithConfig.receive("key", message, consumer, partition, offset)

            // Wait for DLQ exceptions
            dlqExceptionLatch.await(5, TimeUnit.SECONDS)
            Thread.sleep(200)

            // Assert - Should have attempted DLQ but no fallback
            verify(dlqPublisher, atLeast(2)).publishToDlq(any(), any(), any())
            verify(consumer, never()).commitSync(any(), any()) // No commit because DLQ failed
        }

        @Test
        fun `should use fallback when DLQ exception occurs and max retries exceeded`() {
            // Arrange
            val message = createTestMessage()
            val partition = 0
            val offset = 100L
            val testConfig = config.copy(enableDlqFallback = true, dlqMaxRetries = 1)
            val consumerWithFallback =
                TestKafkaConsumerWithFallback(
                    processor,
                    dlqPublisher,
                    metrics,
                    testConfig,
                    processingExecutor,
                    fallbackResult = true,
                )
            val dlqExceptionLatch = CountDownLatch(1)

            doAnswer { throw ProcessingException("Processing failed") }.whenever(processor).process(any())
            var dlqCallCount = 0
            whenever(dlqPublisher.publishToDlq(any(), any(), any())).thenAnswer {
                dlqCallCount++
                if (dlqCallCount >= 2) { // After first attempt + 1 retry
                    dlqExceptionLatch.countDown()
                }
                throw RuntimeException("DLQ publish exception")
            }

            // Act
            consumerWithFallback.receive("key", message, consumer, partition, offset)

            // Wait for DLQ exceptions and fallback
            dlqExceptionLatch.await(5, TimeUnit.SECONDS)
            Thread.sleep(200) // Wait for fallback

            // Assert - Should have attempted DLQ, then used fallback
            verify(dlqPublisher, atLeast(2)).publishToDlq(any(), any(), any())
        }

        @Test
        fun `should detect shutdown before DLQ exception retry delay`() {
            // Arrange
            val message = createTestMessage()
            val partition = 0
            val offset = 100L
            val testConfig = config.copy(dlqMaxRetries = 3, initialRetryDelayMs = 500L)
            val consumerWithConfig = TestKafkaConsumer(processor, dlqPublisher, metrics, testConfig, processingExecutor)
            val dlqExceptionLatch = CountDownLatch(1)
            val shutdownLatch = CountDownLatch(1)

            doAnswer { throw ProcessingException("Processing failed") }.whenever(processor).process(any())
            var dlqCallCount = 0
            whenever(dlqPublisher.publishToDlq(any(), any(), any())).thenAnswer {
                dlqCallCount++
                if (dlqCallCount == 1) {
                    dlqExceptionLatch.countDown()
                }
                throw RuntimeException("DLQ publish exception")
            }

            // Act - Start processing
            consumerWithConfig.receive("key", message, consumer, partition, offset)

            // Wait for DLQ exception
            dlqExceptionLatch.await(2, TimeUnit.SECONDS)

            // Shutdown before exception retry delay (should detect shutdown before sleep)
            Thread {
                Thread.sleep(50) // Small delay
                consumerWithConfig.shutdown()
                shutdownLatch.countDown()
            }.start()

            // Wait for shutdown
            shutdownLatch.await(3, TimeUnit.SECONDS)
            Thread.sleep(100)

            // Assert - Should have detected shutdown before retry delay
            verify(dlqPublisher, atLeast(1)).publishToDlq(any(), any(), any())
        }

        @Test
        fun `should detect shutdown after DLQ exception retry delay`() {
            // Arrange
            val message = createTestMessage()
            val partition = 0
            val offset = 100L
            val testConfig = config.copy(dlqMaxRetries = 3, initialRetryDelayMs = 100L)
            val consumerWithConfig = TestKafkaConsumer(processor, dlqPublisher, metrics, testConfig, processingExecutor)
            val dlqExceptionLatch = CountDownLatch(1)
            val shutdownLatch = CountDownLatch(1)

            doAnswer { throw ProcessingException("Processing failed") }.whenever(processor).process(any())
            var dlqCallCount = 0
            whenever(dlqPublisher.publishToDlq(any(), any(), any())).thenAnswer {
                dlqCallCount++
                if (dlqCallCount == 1) {
                    dlqExceptionLatch.countDown()
                }
                throw RuntimeException("DLQ publish exception")
            }

            // Act - Start processing
            consumerWithConfig.receive("key", message, consumer, partition, offset)

            // Wait for DLQ exception
            dlqExceptionLatch.await(2, TimeUnit.SECONDS)

            // Shutdown during exception retry delay (should detect shutdown after sleep)
            Thread {
                Thread.sleep(150) // Wait for sleep to start
                consumerWithConfig.shutdown()
                shutdownLatch.countDown()
            }.start()

            // Wait for shutdown
            shutdownLatch.await(3, TimeUnit.SECONDS)
            Thread.sleep(100)

            // Assert - Should have detected shutdown after retry delay
            verify(dlqPublisher, atLeast(1)).publishToDlq(any(), any(), any())
        }

        @Test
        fun `should not use DLQ fallback when disabled`() {
            // Arrange
            val message = createTestMessage("test-1")
            val partition = 0
            val offset = 100L
            val testConfig = config.copy(enableDlqFallback = false, dlqMaxRetries = 1)
            val consumerWithConfig = TestKafkaConsumer(processor, dlqPublisher, metrics, testConfig, processingExecutor)
            val dlqAttempts = AtomicInteger(0)
            val allDlqAttemptsLatch = CountDownLatch(1)

            // First message fails
            doAnswer { throw ProcessingException("Processing failed") }.whenever(processor).process(any())
            // DLQ fails for first message (match by message content to avoid interference)
            whenever(dlqPublisher.publishToDlq(eq(message), any(), any())).thenAnswer {
                val attempts = dlqAttempts.incrementAndGet()
                if (attempts >= 2) { // Initial + 1 retry
                    allDlqAttemptsLatch.countDown()
                }
                false
            }

            // Act
            consumerWithConfig.receive("key", message, consumer, partition, offset)

            // Wait for all DLQ attempts
            allDlqAttemptsLatch.await(5, TimeUnit.SECONDS)

            // Allow time for whenComplete handler to run and verify no commit was queued
            Thread.sleep(200)

            // Assert
            // Verify DLQ was called exactly 2 times for the first message (initial + 1 retry)
            verify(dlqPublisher, times(2)).publishToDlq(eq(message), any(), any())
            // Verify no commit happened because DLQ failed and fallback is disabled
            verify(consumer, never()).commitSync(any(), any()) // No commit, no fallback
        }
    }

    @Nested
    @DisplayName("Shutdown Behavior Tests")
    inner class ShutdownBehaviorTests {
        @Test
        fun `should reject new messages during shutdown`() {
            // Arrange
            val message = createTestMessage()
            val partition = 0
            val offset = 100L

            // Act - Shutdown first
            testConsumer.shutdown()

            // Try to receive message
            testConsumer.receive("key", message, consumer, partition, offset)

            // Wait a moment for any processing attempts
            Thread.sleep(100)

            // Assert
            verify(processor, never()).process(any())
            verify(consumer).pause(any())
        }

        @Test
        fun `should abort in-flight retries during shutdown`() {
            // Arrange
            val message = createTestMessage()
            val partition = 0
            val offset = 100L
            val testConfig = config.copy(maxRetries = 5, initialRetryDelayMs = 200L)
            val consumerWithConfig = TestKafkaConsumer(processor, dlqPublisher, metrics, testConfig, processingExecutor)
            val processingStarted = CountDownLatch(1)
            val shutdownLatch = CountDownLatch(1)

            doAnswer {
                processingStarted.countDown()
                throw ProcessingException("Retry test")
            }.whenever(processor).process(any())

            // Act - Start processing
            consumerWithConfig.receive("key", message, consumer, partition, offset)
            processingStarted.await(1, TimeUnit.SECONDS)

            // Shutdown during retry (before all retries complete)
            Thread.sleep(100) // Let it start retry
            Thread {
                consumerWithConfig.shutdown()
                shutdownLatch.countDown()
            }.start()

            // Wait for shutdown to complete
            shutdownLatch.await(3, TimeUnit.SECONDS)

            // Assert
            // Should not have exhausted all retries
            verify(processor, atMost(3)).process(any())
            verify(dlqPublisher, never()).publishToDlq(any(), any(), any())
        }

        @Test
        fun `should detect shutdown before retry delay in processWithRetry`() {
            // Arrange
            val message = createTestMessage()
            val partition = 0
            val offset = 100L
            val testConfig = config.copy(maxRetries = 3, initialRetryDelayMs = 500L)
            val consumerWithConfig = TestKafkaConsumer(processor, dlqPublisher, metrics, testConfig, processingExecutor)
            val retryStarted = CountDownLatch(1)

            var callCount = 0
            doAnswer {
                callCount++
                if (callCount == 1) {
                    retryStarted.countDown()
                }
                throw ProcessingException("Retry test")
            }.whenever(processor).process(any())

            // Act - Start processing
            consumerWithConfig.receive("key", message, consumer, partition, offset)
            retryStarted.await(2, TimeUnit.SECONDS)

            // Shutdown before retry delay completes
            Thread.sleep(50) // Small delay to ensure we're in the delay period
            consumerWithConfig.shutdown()

            // Wait for shutdown to be detected
            Thread.sleep(200)

            // Assert - Should detect shutdown before delay
            verify(processor, atLeast(1)).process(any())
        }

        @Test
        fun `should detect shutdown after retry delay in processWithRetry`() {
            // Arrange
            val message = createTestMessage()
            val partition = 0
            val offset = 100L
            val testConfig = config.copy(maxRetries = 3, initialRetryDelayMs = 100L)
            val consumerWithConfig = TestKafkaConsumer(processor, dlqPublisher, metrics, testConfig, processingExecutor)
            val retryStarted = CountDownLatch(1)

            var callCount = 0
            doAnswer {
                callCount++
                if (callCount == 1) {
                    retryStarted.countDown()
                }
                throw ProcessingException("Retry test")
            }.whenever(processor).process(any())

            // Act - Start processing
            consumerWithConfig.receive("key", message, consumer, partition, offset)
            retryStarted.await(2, TimeUnit.SECONDS)

            // Shutdown after retry delay completes but before next attempt
            Thread.sleep(150) // Wait for delay to complete
            consumerWithConfig.shutdown()

            // Wait for shutdown to be detected
            Thread.sleep(100)

            // Assert - Should detect shutdown after delay
            verify(processor, atLeast(1)).process(any())
        }

        @Test
        fun `should abort DLQ retry during shutdown`() {
            // Arrange
            val message = createTestMessage()
            val partition = 0
            val offset = 100L
            val testConfig = config.copy(dlqMaxRetries = 5, initialRetryDelayMs = 200L)
            val consumerWithConfig = TestKafkaConsumer(processor, dlqPublisher, metrics, testConfig, processingExecutor)
            val dlqStarted = CountDownLatch(1)
            val shutdownLatch = CountDownLatch(1)

            doAnswer { throw ProcessingException("Processing failed") }.whenever(processor).process(any())
            whenever(dlqPublisher.publishToDlq(any(), any(), any())).thenAnswer {
                dlqStarted.countDown()
                false
            }

            // Act - Start processing
            consumerWithConfig.receive("key", message, consumer, partition, offset)

            // Wait for DLQ to start
            dlqStarted.await(2, TimeUnit.SECONDS)

            // Shutdown during DLQ retry
            Thread {
                consumerWithConfig.shutdown()
                shutdownLatch.countDown()
            }.start()

            // Wait for shutdown to complete
            shutdownLatch.await(3, TimeUnit.SECONDS)

            // Assert
            // Should not have exhausted all DLQ retries
            verify(dlqPublisher, atMost(3)).publishToDlq(any(), any(), any())
        }

        @Test
        fun `should detect shutdown before DLQ retry delay`() {
            // Arrange
            val message = createTestMessage()
            val partition = 0
            val offset = 100L
            val testConfig = config.copy(dlqMaxRetries = 3, initialRetryDelayMs = 500L)
            val consumerWithConfig = TestKafkaConsumer(processor, dlqPublisher, metrics, testConfig, processingExecutor)
            val dlqAttemptLatch = CountDownLatch(1)
            val shutdownLatch = CountDownLatch(1)

            doAnswer { throw ProcessingException("Processing failed") }.whenever(processor).process(any())
            var dlqCallCount = 0
            whenever(dlqPublisher.publishToDlq(any(), any(), any())).thenAnswer {
                dlqCallCount++
                if (dlqCallCount == 1) {
                    dlqAttemptLatch.countDown()
                }
                false // Always fail to trigger retry
            }

            // Act - Start processing
            consumerWithConfig.receive("key", message, consumer, partition, offset)

            // Wait for first DLQ attempt
            dlqAttemptLatch.await(2, TimeUnit.SECONDS)

            // Shutdown before the retry delay completes
            Thread.sleep(50) // Small delay to ensure we're in the delay period
            Thread {
                consumerWithConfig.shutdown()
                shutdownLatch.countDown()
            }.start()

            // Wait for shutdown
            shutdownLatch.await(2, TimeUnit.SECONDS)

            // Assert - Should detect shutdown before delay completes
            verify(dlqPublisher, atLeast(1)).publishToDlq(any(), any(), any())
        }

        @Test
        fun `should detect shutdown after DLQ retry delay`() {
            // Arrange
            val message = createTestMessage()
            val partition = 0
            val offset = 100L
            val testConfig = config.copy(dlqMaxRetries = 3, initialRetryDelayMs = 100L)
            val consumerWithConfig = TestKafkaConsumer(processor, dlqPublisher, metrics, testConfig, processingExecutor)
            val dlqAttemptLatch = CountDownLatch(1)

            doAnswer { throw ProcessingException("Processing failed") }.whenever(processor).process(any())
            var dlqCallCount = 0
            whenever(dlqPublisher.publishToDlq(any(), any(), any())).thenAnswer {
                dlqCallCount++
                if (dlqCallCount == 1) {
                    dlqAttemptLatch.countDown()
                }
                false // Always fail to trigger retry
            }

            // Act - Start processing
            consumerWithConfig.receive("key", message, consumer, partition, offset)

            // Wait for first DLQ attempt
            dlqAttemptLatch.await(2, TimeUnit.SECONDS)

            // Shutdown after the retry delay completes but before next attempt
            Thread.sleep(150) // Wait for delay to complete
            consumerWithConfig.shutdown()

            // Wait a bit for shutdown to be detected
            Thread.sleep(100)

            // Assert - Should detect shutdown after delay
            verify(dlqPublisher, atLeast(1)).publishToDlq(any(), any(), any())
        }

        @Test
        fun `should detect shutdown during DLQ exception retry delay`() {
            // Arrange
            val message = createTestMessage()
            val partition = 0
            val offset = 100L
            val testConfig = config.copy(dlqMaxRetries = 3, initialRetryDelayMs = 200L)
            val consumerWithConfig = TestKafkaConsumer(processor, dlqPublisher, metrics, testConfig, processingExecutor)
            val dlqExceptionLatch = CountDownLatch(1)

            doAnswer { throw ProcessingException("Processing failed") }.whenever(processor).process(any())
            var dlqCallCount = 0
            whenever(dlqPublisher.publishToDlq(any(), any(), any())).thenAnswer {
                dlqCallCount++
                if (dlqCallCount == 1) {
                    dlqExceptionLatch.countDown()
                }
                throw RuntimeException("DLQ publish exception") // Throw exception to test exception path
            }

            // Act - Start processing
            consumerWithConfig.receive("key", message, consumer, partition, offset)

            // Wait for first DLQ exception
            dlqExceptionLatch.await(2, TimeUnit.SECONDS)

            // Shutdown during exception retry delay
            Thread.sleep(50) // Small delay
            consumerWithConfig.shutdown()

            // Wait for shutdown to be detected
            Thread.sleep(100)

            // Assert - Should handle shutdown during exception retry
            verify(dlqPublisher, atLeast(1)).publishToDlq(any(), any(), any())
        }

        @Test
        fun `should wait for in-flight tasks during shutdown`() {
            // Arrange
            val message1 = createTestMessage("test-1")
            val message2 = createTestMessage("test-2")
            val partition1 = 0
            val partition2 = 1
            val processingLatch = CountDownLatch(2)
            val releaseLatch = CountDownLatch(1)
            val processedMessages = mutableSetOf<String>()

            whenever(processor.process(any())).thenAnswer {
                val msg = it.arguments[0] as TestMessage
                synchronized(processedMessages) {
                    processedMessages.add(msg.recordId)
                }
                processingLatch.countDown()
                releaseLatch.await(2, TimeUnit.SECONDS) // Block until released
            }

            // Act - Submit multiple tasks for different partitions (so they can run in parallel)
            testConsumer.receive("key1", message1, consumer, partition1, 100L)
            testConsumer.receive("key2", message2, consumer, partition2, 100L)

            // Wait for both to start processing (different partitions allow parallel execution)
            processingLatch.await(1, TimeUnit.SECONDS)

            // Shutdown in separate thread - should wait for both in-flight tasks
            val shutdownThread =
                Thread {
                    testConsumer.shutdown()
                }
            shutdownThread.start()

            // Wait a bit to ensure shutdown started waiting
            Thread.sleep(100)

            // Release processing
            releaseLatch.countDown()
            shutdownThread.join(5000)
            Thread.sleep(200) // Give a bit more time for completion

            // Assert - Both messages should have been processed
            assertThat(processedMessages).containsExactlyInAnyOrder("test-1", "test-2")
            verify(processor, times(2)).process(any())
            // Shutdown should have completed after tasks finished
        }

        @Test
        fun `should handle shutdown timeout gracefully`() {
            // Arrange
            val message = createTestMessage()
            val partition = 0
            val testConfig = config.copy(shutdownTimeoutSeconds = 1L)
            val consumerWithConfig = TestKafkaConsumer(processor, dlqPublisher, metrics, testConfig, processingExecutor)
            val processingLatch = CountDownLatch(1)
            val shutdownLatch = CountDownLatch(1)

            whenever(processor.process(any())).thenAnswer {
                processingLatch.countDown()
                Thread.sleep(3000) // Block longer than timeout
            }

            // Act
            consumerWithConfig.receive("key", message, consumer, partition, 100L)
            processingLatch.await(1, TimeUnit.SECONDS)

            val startTime = System.currentTimeMillis()
            Thread {
                consumerWithConfig.shutdown()
                shutdownLatch.countDown()
            }.start()

            shutdownLatch.await(3, TimeUnit.SECONDS)
            val duration = System.currentTimeMillis() - startTime

            // Assert
            // Should timeout and complete shutdown
            assertThat(duration).isLessThan(2000) // Should timeout around 1 second
        }

        @Test
        fun `should pause all assigned partitions during shutdown`() {
            // Arrange
            val partition1 = TopicPartition("test.topic.v1", 0)
            val partition2 = TopicPartition("test.topic.v1", 1)
            val processingLatch = CountDownLatch(1)
            whenever(consumer.assignment()).thenReturn(setOf(partition1, partition2))

            doAnswer {
                processingLatch.countDown()
            }.whenever(processor).process(any())

            // Act
            testConsumer.receive("key", createTestMessage(), consumer, 0, 100L)

            // Wait for processing to start (sets lastConsumer)
            processingLatch.await(1, TimeUnit.SECONDS)

            testConsumer.shutdown()

            // Wait for pause to be called
            waitForVerification({
                verify(consumer).pause(setOf(partition1, partition2))
            })
        }

        @Test
        fun `should handle empty assignments when pausing partitions during shutdown`() {
            // Arrange
            val processingLatch = CountDownLatch(1)
            whenever(consumer.assignment()).thenReturn(emptySet())

            doAnswer {
                processingLatch.countDown()
            }.whenever(processor).process(any())

            // Act
            testConsumer.receive("key", createTestMessage(), consumer, 0, 100L)

            // Wait for processing to start (sets lastConsumer)
            processingLatch.await(1, TimeUnit.SECONDS)

            testConsumer.shutdown()

            // Wait a bit for shutdown to complete
            Thread.sleep(100)

            // Assert - Should not throw exception, should handle empty assignments gracefully
            verify(consumer).assignment()
            verify(consumer, never()).pause(any())
        }

        @Test
        fun `should handle exception when pausing partitions during shutdown`() {
            // Arrange
            val partition1 = TopicPartition("test.topic.v1", 0)
            val partition2 = TopicPartition("test.topic.v1", 1)
            val processingLatch = CountDownLatch(1)
            whenever(consumer.assignment()).thenReturn(setOf(partition1, partition2))
            doThrow(RuntimeException("Pause error")).whenever(consumer).pause(any())

            doAnswer {
                processingLatch.countDown()
            }.whenever(processor).process(any())

            // Act
            testConsumer.receive("key", createTestMessage(), consumer, 0, 100L)

            // Wait for processing to start (sets lastConsumer)
            processingLatch.await(1, TimeUnit.SECONDS)

            // Should not throw exception
            testConsumer.shutdown()

            // Wait a bit for shutdown to complete
            Thread.sleep(100)

            // Assert - Should have attempted to pause, but handled exception gracefully
            verify(consumer).pause(any())
        }

        @Test
        fun `should handle shutdown when lastConsumer is null`() {
            // Act
            testConsumer.shutdown()

            // Assert - Should not throw exception
            // No consumer to pause, but shutdown should complete
        }

        @Test
        fun `should handle shutdown when assignments are empty`() {
            // Arrange
            val processingLatch = CountDownLatch(1)
            whenever(consumer.assignment()).thenReturn(emptySet())

            doAnswer {
                processingLatch.countDown()
            }.whenever(processor).process(any())

            // Act
            testConsumer.receive("key", createTestMessage(), consumer, 0, 100L)

            // Wait for processing to start
            processingLatch.await(1, TimeUnit.SECONDS)

            testConsumer.shutdown()

            // Give a moment for shutdown to complete
            Thread.sleep(100)

            // Assert - Should not throw exception
            verify(consumer).assignment()
        }

        @Test
        fun `should handle exception when consumer assignment throws during shutdown`() {
            // Arrange
            val processingLatch = CountDownLatch(1)
            val partition1 = TopicPartition("test.topic.v1", 0)
            val partition2 = TopicPartition("test.topic.v1", 1)

            doAnswer {
                processingLatch.countDown()
            }.whenever(processor).process(any())

            // First call returns assignments, second call (during shutdown) throws
            var assignmentCallCount = 0
            whenever(consumer.assignment()).thenAnswer {
                assignmentCallCount++
                if (assignmentCallCount == 1) {
                    setOf(partition1, partition2)
                } else {
                    throw RuntimeException("Assignment error during shutdown")
                }
            }

            // Act
            testConsumer.receive("key", createTestMessage(), consumer, 0, 100L)

            // Wait for processing to start (sets lastConsumer)
            processingLatch.await(1, TimeUnit.SECONDS)

            // Shutdown should handle exception gracefully
            testConsumer.shutdown()

            // Give a moment for shutdown to complete
            Thread.sleep(200)

            // Assert - Should not throw exception
            // Should have called assignment() at least once
            verify(consumer, atLeast(1)).assignment()
        }

        @Test
        fun `should handle shutdown when already shutting down`() {
            // Act
            testConsumer.shutdown()
            testConsumer.shutdown() // Second call

            // Assert - Should not throw exception or do duplicate work
            // Both calls should complete successfully
        }

        @Test
        fun `should flush pending commits during shutdown`() {
            // Arrange
            val message = createTestMessage()
            val partition = 0
            val offset = 100L
            val processingLatch = CountDownLatch(1)

            doAnswer {
                processingLatch.countDown()
            }.whenever(processor).process(any())

            // Act - Process message but don't trigger commit flush
            testConsumer.receive("key", message, consumer, partition, offset)

            // Wait for processing
            processingLatch.await(2, TimeUnit.SECONDS)

            // Allow time for whenComplete handler to add commit to pendingCommits
            Thread.sleep(100)

            // Shutdown should flush pending commits
            testConsumer.shutdown()

            // Wait for commit to be processed
            waitForVerification({
                val topicPartition = TopicPartition("test.topic.v1", partition)
                verify(consumer).commitSync(
                    mapOf(topicPartition to OffsetAndMetadata(offset + 1)),
                    Duration.ofSeconds(5),
                )
            })
        }

        @Test
        fun `should reject messages arriving during shutdown`() {
            // Arrange
            val message1 = createTestMessage("test-1")
            val message2 = createTestMessage("test-2")
            val partition = 0
            val processingLatch = CountDownLatch(1)
            val shutdownStarted = CountDownLatch(1)

            doAnswer {
                processingLatch.countDown()
                Thread.sleep(100) // Simulate processing time
            }.whenever(processor).process(any())

            // Act - Start processing first message
            testConsumer.receive("key1", message1, consumer, partition, 100L)
            processingLatch.await(1, TimeUnit.SECONDS)

            // Start shutdown in separate thread
            val shutdownThread =
                Thread {
                    shutdownStarted.countDown()
                    testConsumer.shutdown()
                }
            shutdownThread.start()

            // Wait for shutdown to start
            shutdownStarted.await(1, TimeUnit.SECONDS)
            Thread.sleep(50) // Give shutdown time to set flag

            // Try to receive message during shutdown
            testConsumer.receive("key2", message2, consumer, partition, 101L)

            // Wait a moment for any processing attempts
            Thread.sleep(200)

            // Assert - Second message should be rejected
            verify(processor, times(1)).process(eq(message1)) // Only first message processed
            verify(processor, never()).process(eq(message2)) // Second message rejected
            verify(consumer).pause(any()) // Partition should be paused
        }
    }

    @Nested
    @DisplayName("Commit Management Tests")
    inner class CommitManagementTests {
        @Test
        fun `should flush multiple pending commits from different partitions`() {
            // Arrange
            val message1 = createTestMessage("test-1")
            val message2 = createTestMessage("test-2")
            val message3 = createTestMessage("test-3")
            val partition1 = 0
            val partition2 = 1
            val processingLatch = CountDownLatch(2)

            doAnswer {
                processingLatch.countDown()
            }.whenever(processor).process(any())

            // Act - Process messages from different partitions
            testConsumer.receive("key1", message1, consumer, partition1, 100L)
            testConsumer.receive("key2", message2, consumer, partition2, 100L)

            // Wait for both to process
            processingLatch.await(2, TimeUnit.SECONDS)

            // Allow time for whenComplete handlers to add commits to pendingCommits
            Thread.sleep(100)

            // Trigger commit flush with a third message
            testConsumer.receive("key3", message3, consumer, partition1, 101L)

            // Wait for commits to be processed
            waitForVerification({
                val topicPartition1 = TopicPartition("test.topic.v1", partition1)
                val topicPartition2 = TopicPartition("test.topic.v1", partition2)
                verify(consumer).commitSync(
                    mapOf(topicPartition1 to OffsetAndMetadata(100L + 1)),
                    Duration.ofSeconds(5),
                )
                verify(consumer).commitSync(
                    mapOf(topicPartition2 to OffsetAndMetadata(100L + 1)),
                    Duration.ofSeconds(5),
                )
            })
        }

        @Test
        fun `should handle CommitFailedException and retry commit`() {
            // Arrange
            val message = createTestMessage()
            val partition = 0
            val offset = 100L
            val commitCallCount = AtomicInteger(0)
            val processingLatch = CountDownLatch(1)

            doAnswer {
                processingLatch.countDown()
            }.whenever(processor).process(any())
            doAnswer {
                val count = commitCallCount.incrementAndGet()
                if (count == 1) {
                    throw CommitFailedException("Commit failed")
                }
            }.whenever(consumer).commitSync(any(), any())

            // Act
            testConsumer.receive("key", message, consumer, partition, offset)

            // Wait for processing
            processingLatch.await(2, TimeUnit.SECONDS)

            // Allow time for whenComplete handler to add commit to pendingCommits
            Thread.sleep(100)

            // Trigger commit flush
            testConsumer.receive("key2", createTestMessage("test-2"), consumer, partition, offset + 1)

            // Wait for commit retry
            waitForVerification({
                verify(consumer, times(2)).commitSync(any(), any()) // Initial + retry
            })
        }

        @Test
        fun `should handle commit retry failure gracefully`() {
            // Arrange
            val message = createTestMessage()
            val partition = 0
            val offset = 100L
            val processingLatch = CountDownLatch(1)

            doAnswer {
                processingLatch.countDown()
            }.whenever(processor).process(any())
            doThrow(CommitFailedException("Commit failed")).whenever(consumer).commitSync(any(), any())

            // Act
            testConsumer.receive("key", message, consumer, partition, offset)

            // Wait for processing
            processingLatch.await(2, TimeUnit.SECONDS)

            // Allow time for whenComplete handler to add commit to pendingCommits
            Thread.sleep(100)

            // Trigger commit flush
            testConsumer.receive("key2", createTestMessage("test-2"), consumer, partition, offset + 1)

            // Wait for commit attempts
            waitForVerification({
                // Should attempt retry but fail again - no exception thrown
                verify(consumer, atLeast(2)).commitSync(any(), any())
            })
        }

        @Test
        fun `should handle generic commit exception`() {
            // Arrange
            val message = createTestMessage()
            val partition = 0
            val offset = 100L
            val processingLatch = CountDownLatch(1)

            doAnswer {
                processingLatch.countDown()
            }.whenever(processor).process(any())
            doThrow(RuntimeException("Unexpected error")).whenever(consumer).commitSync(any(), any())

            // Act
            testConsumer.receive("key", message, consumer, partition, offset)

            // Wait for processing
            processingLatch.await(2, TimeUnit.SECONDS)

            // Allow time for whenComplete handler to add commit to pendingCommits
            Thread.sleep(100)

            // Trigger commit flush
            testConsumer.receive("key2", createTestMessage("test-2"), consumer, partition, offset + 1)

            // Wait for commit attempt
            waitForVerification({
                // Should not throw exception
                verify(consumer).commitSync(any(), any())
            })
        }

        @Test
        fun `should commit with correct offset (offset + 1)`() {
            // Arrange
            val message = createTestMessage()
            val partition = 0
            val offset = 100L
            val processingLatch = CountDownLatch(1)

            doAnswer {
                processingLatch.countDown()
            }.whenever(processor).process(any())

            // Act
            testConsumer.receive("key", message, consumer, partition, offset)

            // Wait for processing
            processingLatch.await(2, TimeUnit.SECONDS)

            // Allow time for whenComplete handler to add commit to pendingCommits
            Thread.sleep(100)

            // Trigger commit flush
            testConsumer.receive("key2", createTestMessage("test-2"), consumer, partition, offset + 1)

            // Wait for commit and verify
            waitForVerification({
                val topicPartition = TopicPartition("test.topic.v1", partition)
                val offsetCaptor = argumentCaptor<Map<TopicPartition, OffsetAndMetadata>>()
                verify(consumer).commitSync(offsetCaptor.capture(), any())
                val committedOffset = offsetCaptor.firstValue[topicPartition]
                assertThat(committedOffset).isNotNull
                assertThat(committedOffset!!.offset()).isEqualTo(offset + 1)
            })
        }

        @Test
        fun `should distinguish processing success vs DLQ success in commit`() {
            // Arrange
            val successMessage = createTestMessage("success")
            val dlqMessage = createTestMessage("dlq")
            val partition = 0
            val successLatch = CountDownLatch(1)
            val dlqLatch = CountDownLatch(1)

            doAnswer {
                successLatch.countDown()
            }.whenever(processor).process(eq(successMessage))
            whenever(processor.process(eq(dlqMessage))).thenAnswer {
                dlqLatch.countDown()
                throw ValidationException("Invalid")
            }
            whenever(dlqPublisher.publishToDlq(eq(dlqMessage), any(), any())).thenReturn(true)

            // Act
            testConsumer.receive("key1", successMessage, consumer, partition, 100L)
            successLatch.await(2, TimeUnit.SECONDS)

            testConsumer.receive("key2", dlqMessage, consumer, partition, 101L)
            dlqLatch.await(2, TimeUnit.SECONDS)

            // Allow time for whenComplete handlers to add commits to pendingCommits
            Thread.sleep(100)

            // Trigger commit flush
            testConsumer.receive("key3", createTestMessage("test-3"), consumer, partition, 102L)

            // Wait for commits
            waitForVerification({
                // Both should commit, but with different flags
                verify(consumer, times(2)).commitSync(any(), any())
            })
        }

        @Test
        fun `should handle shutdown during commit retry`() {
            // Arrange
            val message = createTestMessage()
            val partition = 0
            val offset = 100L
            val processingLatch = CountDownLatch(1)
            val commitCallCount = AtomicInteger(0)
            val shutdownLatch = CountDownLatch(1)

            doAnswer {
                processingLatch.countDown()
            }.whenever(processor).process(any())
            doAnswer {
                val count = commitCallCount.incrementAndGet()
                if (count == 1) {
                    throw CommitFailedException("Commit failed")
                }
                // Second call (retry) should succeed, but shutdown might interrupt
            }.whenever(consumer).commitSync(any(), any())

            // Act - Process message
            testConsumer.receive("key", message, consumer, partition, offset)
            processingLatch.await(2, TimeUnit.SECONDS)

            // Allow time for whenComplete handler to add commit to pendingCommits
            Thread.sleep(100)

            // Trigger commit flush (will fail first time)
            testConsumer.receive("key2", createTestMessage("test-2"), consumer, partition, offset + 1)

            // Wait for first commit attempt to fail
            waitForVerification({
                verify(consumer, atLeast(1)).commitSync(any(), any())
            })

            // Shutdown during commit retry
            Thread {
                testConsumer.shutdown()
                shutdownLatch.countDown()
            }.start()

            // Wait for shutdown to complete
            shutdownLatch.await(3, TimeUnit.SECONDS)

            // Assert - Should have attempted commit at least once
            // Shutdown should complete gracefully even if commit retry is interrupted
            verify(consumer, atLeast(1)).commitSync(any(), any())
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    inner class ErrorHandlingTests {
        @Test
        fun `should handle unexpected failure in processing task`() {
            // Arrange
            val message = createTestMessage()
            val partition = 0
            val offset = 100L
            var getRecordIdCallCount = 0
            val dlqLatch = CountDownLatch(1)

            // Test unexpected failure by making processor.getRecordId throw on first call
            // but work on second call (for handleUnexpectedFailure)
            whenever(processor.getRecordId(any())).thenAnswer {
                getRecordIdCallCount++
                if (getRecordIdCallCount == 1) {
                    throw RuntimeException("Unexpected error in getRecordId")
                }
                (it.arguments[0] as TestMessage).recordId
            }
            whenever(dlqPublisher.publishToDlq(any(), any(), any())).thenAnswer {
                dlqLatch.countDown()
                true
            }

            // Act
            testConsumer.receive("key", message, consumer, partition, offset)

            // Wait for DLQ publish
            dlqLatch.await(2, TimeUnit.SECONDS)

            // Allow time for whenComplete handler to add commit to pendingCommits
            Thread.sleep(100)

            // Trigger commit flush
            testConsumer.receive("key2", createTestMessage("test-2"), consumer, partition, offset + 1)

            // Wait for commit
            waitForVerification({
                verify(consumer).commitSync(any(), any())
            })

            // Assert - Exception in getRecordId will cause the task to fail
            // and be caught in whenComplete handler
            verify(metrics).incrementError()
            verify(dlqPublisher).publishToDlq(any(), any(), eq(0))
        }

        @Test
        fun `should handle WakeupException when waking consumer`() {
            // Arrange
            val message = createTestMessage()
            val partition = 0
            val offset = 100L
            val processingLatch = CountDownLatch(1)

            doAnswer {
                processingLatch.countDown()
            }.whenever(processor).process(any())
            doThrow(WakeupException()).whenever(consumer).wakeup()

            // Act
            testConsumer.receive("key", message, consumer, partition, offset)

            // Wait for processing
            processingLatch.await(2, TimeUnit.SECONDS)

            // Allow time for whenComplete handler to add commit to pendingCommits
            Thread.sleep(100)

            // Trigger commit flush - should still process pending commit
            testConsumer.receive("key2", createTestMessage("test-2"), consumer, partition, offset + 1)

            // Wait for commit
            waitForVerification({
                // Commit should still happen
                verify(consumer).commitSync(any(), any())
            })
        }

        @Test
        fun `should handle generic exception when waking consumer`() {
            // Arrange
            val message = createTestMessage()
            val partition = 0
            val offset = 100L
            val processingLatch = CountDownLatch(1)

            doAnswer {
                processingLatch.countDown()
            }.whenever(processor).process(any())
            doThrow(RuntimeException("Wakeup error")).whenever(consumer).wakeup()

            // Act
            testConsumer.receive("key", message, consumer, partition, offset)

            // Wait for processing
            processingLatch.await(2, TimeUnit.SECONDS)

            // Allow time for whenComplete handler to add commit to pendingCommits
            Thread.sleep(100)

            // Trigger commit flush
            testConsumer.receive("key2", createTestMessage("test-2"), consumer, partition, offset + 1)

            // Wait for commit
            waitForVerification({
                // Commit should still happen
                verify(consumer).commitSync(any(), any())
            })
        }

        @Test
        fun `should handle pause partition exception`() {
            // Arrange
            val message = createTestMessage()
            val partition = 0

            doThrow(RuntimeException("Pause error")).whenever(consumer).pause(any())

            // Act - Shutdown first to trigger pause
            testConsumer.shutdown()
            testConsumer.receive("key", message, consumer, partition, 100L)

            // Give a moment for pause attempt
            Thread.sleep(100)

            // Assert - Should not throw exception
            verify(consumer).pause(any())
        }

        @Test
        fun `should handle exception in getRecordId during handleUnexpectedFailure`() {
            // Arrange
            val message = createTestMessage()
            val partition = 0
            val offset = 100L
            var getRecordIdCallCount = 0

            // Make getRecordId throw on first call (in processMessage), but work on second call (in handleUnexpectedFailure)
            // This simulates a scenario where getRecordId fails during normal processing but works during error handling
            whenever(processor.getRecordId(any())).thenAnswer {
                getRecordIdCallCount++
                if (getRecordIdCallCount == 1) {
                    throw RuntimeException("Unexpected error in getRecordId during processing")
                }
                // Second call (in handleUnexpectedFailure) should succeed
                (it.arguments[0] as TestMessage).recordId
            }
            whenever(dlqPublisher.publishToDlq(any(), any(), any())).thenReturn(true)

            // Act - This will cause exception in processing task (getRecordId throws), which triggers handleUnexpectedFailure
            testConsumer.receive("key", message, consumer, partition, offset)

            // Wait a bit for the exception handling to complete
            Thread.sleep(500)

            // Assert - Should handle the exception gracefully
            // getRecordId fails in processMessage, but succeeds in handleUnexpectedFailure
            verify(metrics).incrementError()
            verify(dlqPublisher).publishToDlq(any(), any(), eq(0))
        }

        @Test
        fun `should abort DLQ publish during shutdown when ValidationException occurs`() {
            // Arrange
            val message = createTestMessage()
            val partition = 0
            val offset = 100L
            val testConfig = config.copy(dlqMaxRetries = 5, initialRetryDelayMs = 200L)
            val consumerWithConfig = TestKafkaConsumer(processor, dlqPublisher, metrics, testConfig, processingExecutor)
            val dlqStarted = CountDownLatch(1)
            val shutdownLatch = CountDownLatch(1)

            doAnswer {
                val msg = it.arguments[0] as TestMessage
                if (msg.recordId == message.recordId) {
                    throw ValidationException("Invalid data")
                }
            }.whenever(processor).process(any())
            whenever(dlqPublisher.publishToDlq(any(), any(), any())).thenAnswer {
                dlqStarted.countDown()
                false // DLQ publish fails, will retry
            }

            // Act - Start processing
            consumerWithConfig.receive("key", message, consumer, partition, offset)

            // Wait for DLQ to start
            dlqStarted.await(2, TimeUnit.SECONDS)

            // Shutdown during DLQ retry
            Thread {
                consumerWithConfig.shutdown()
                shutdownLatch.countDown()
            }.start()

            // Wait for shutdown to complete
            shutdownLatch.await(3, TimeUnit.SECONDS)

            // Assert - Should not have exhausted all DLQ retries
            verify(dlqPublisher, atMost(3)).publishToDlq(any(), any(), any())
        }
    }

    @Nested
    @DisplayName("Partition Locking Tests")
    inner class PartitionLockingTests {
        @Test
        fun `should process messages from different partitions in parallel`() {
            // Arrange
            val message1 = createTestMessage("test-1")
            val message2 = createTestMessage("test-2")
            val partition1 = 0
            val partition2 = 1
            val processingOrder = mutableListOf<String>()
            val processingLatch = CountDownLatch(2)
            val startLatch = CountDownLatch(2)
            val releaseLatch = CountDownLatch(1)

            whenever(processor.process(any())).thenAnswer {
                val msg = it.arguments[0] as TestMessage
                synchronized(processingOrder) {
                    processingOrder.add(msg.recordId)
                }
                startLatch.countDown()
                releaseLatch.await(2, TimeUnit.SECONDS) // Block until released
                processingLatch.countDown()
            }

            // Act - Submit messages for different partitions simultaneously
            testConsumer.receive("key1", message1, consumer, partition1, 100L)
            testConsumer.receive("key2", message2, consumer, partition2, 100L)

            // Wait for both to start processing (should be parallel)
            startLatch.await(1, TimeUnit.SECONDS)

            // Verify both started before releasing
            assertThat(processingOrder.size).isEqualTo(2) // Both should have started

            // Release both
            releaseLatch.countDown()

            // Wait for both to complete
            processingLatch.await(3, TimeUnit.SECONDS)

            // Assert - Both messages should have been processed
            assertThat(processingOrder).containsExactlyInAnyOrder("test-1", "test-2")
            verify(processor, times(2)).process(any())
        }

        @Test
        fun `should process messages sequentially per partition`() {
            // Arrange
            val message1 = createTestMessage("test-1")
            val message2 = createTestMessage("test-2")
            val partition = 0
            val processingOrder = mutableListOf<String>()
            val processingLatch = CountDownLatch(2)

            whenever(processor.process(any())).thenAnswer {
                val msg = it.arguments[0] as TestMessage
                synchronized(processingOrder) {
                    processingOrder.add(msg.recordId)
                }
                processingLatch.countDown()
                Thread.sleep(50) // Simulate processing time
            }

            // Act - Submit messages concurrently for same partition
            testConsumer.receive("key1", message1, consumer, partition, 100L)
            testConsumer.receive("key2", message2, consumer, partition, 101L)

            // Wait for both to complete
            processingLatch.await(3, TimeUnit.SECONDS)

            // Assert - Should process sequentially
            assertThat(processingOrder).hasSize(2)
            // Due to partition locking, order should be deterministic
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    inner class EdgeCasesTests {
        @Test
        fun `should handle empty in-flight tasks during shutdown`() {
            // Act
            testConsumer.shutdown()

            // Assert - Should not throw exception
            // No tasks to wait for
        }

        @Test
        fun `should handle partition lock creation`() {
            // Arrange
            val message1 = createTestMessage("test-1")
            val message2 = createTestMessage("test-2")
            val message3 = createTestMessage("test-3")
            val processingLatch = CountDownLatch(3)

            doAnswer {
                processingLatch.countDown()
            }.whenever(processor).process(any())

            // Act - Process messages for different partitions
            testConsumer.receive("key1", message1, consumer, 0, 100L)
            testConsumer.receive("key2", message2, consumer, 1, 100L)
            testConsumer.receive("key3", message3, consumer, 0, 101L) // Same partition as first

            // Wait for all to process
            processingLatch.await(3, TimeUnit.SECONDS)

            // Assert - All should process
            verify(processor, times(3)).process(any())
        }

        @Test
        fun `should send to DLQ immediately when maxRetries is 0`() {
            // Arrange
            val message = createTestMessage()
            val message2 = createTestMessage("test-2")
            val partition = 0
            val offset = 100L
            val testConfig = config.copy(maxRetries = 0)
            val consumerWithConfig = TestKafkaConsumer(processor, dlqPublisher, metrics, testConfig, processingExecutor)
            val dlqLatch = CountDownLatch(1)

            doAnswer {
                val msg = it.arguments[0] as TestMessage
                if (msg.recordId == message.recordId) {
                    throw ProcessingException("Processing failed")
                }
                // Second message should succeed
            }.whenever(processor).process(any())
            whenever(dlqPublisher.publishToDlq(any(), any(), any())).thenAnswer {
                dlqLatch.countDown()
                true
            }

            // Act
            consumerWithConfig.receive("key", message, consumer, partition, offset)

            // Wait for DLQ publish
            dlqLatch.await(2, TimeUnit.SECONDS)

            // Allow time for whenComplete handler to add commit to pendingCommits
            Thread.sleep(100)

            // Trigger commit flush
            consumerWithConfig.receive("key2", message2, consumer, partition, offset + 1)

            // Wait for commit
            waitForVerification({
                verify(consumer).commitSync(any(), any())
            })

            // Assert - Should process once and send to DLQ immediately (no retries)
            verify(processor, times(1)).process(eq(message))
            verify(metrics, never()).incrementRetry()
            verify(metrics).incrementError("processing")
            verify(dlqPublisher).publishToDlq(eq(message), any(), eq(0))
        }

        @Test
        fun `should handle DLQ retry loop when dlqMaxRetries is 0`() {
            // Arrange
            val message = createTestMessage()
            val partition = 0
            val offset = 100L
            val testConfig = config.copy(dlqMaxRetries = 0)
            val consumerWithConfig = TestKafkaConsumer(processor, dlqPublisher, metrics, testConfig, processingExecutor)
            val dlqLatch = CountDownLatch(1)

            doAnswer { throw ValidationException("Validation failed") }.whenever(processor).process(any())
            whenever(dlqPublisher.publishToDlq(any(), any(), any())).thenAnswer {
                dlqLatch.countDown()
                false // DLQ publish fails
            }

            // Act
            consumerWithConfig.receive("key", message, consumer, partition, offset)

            // Wait for DLQ attempt
            dlqLatch.await(2, TimeUnit.SECONDS)
            Thread.sleep(100)

            // Assert - Should attempt DLQ at least once (no retries since dlqMaxRetries = 0)
            verify(dlqPublisher, atLeast(1)).publishToDlq(any(), any(), any())
            verify(metrics, atLeast(1)).incrementDlqPublishFailure()
        }

        @Test
        fun `should handle DLQ retry loop boundary condition at maxRetries`() {
            // Arrange
            val message = createTestMessage()
            val partition = 0
            val offset = 100L
            val testConfig = config.copy(dlqMaxRetries = 1)
            val consumerWithConfig = TestKafkaConsumer(processor, dlqPublisher, metrics, testConfig, processingExecutor)
            val dlqAttempts = AtomicInteger(0)
            val allAttemptsLatch = CountDownLatch(1)

            doAnswer { throw ValidationException("Validation failed") }.whenever(processor).process(any())
            whenever(dlqPublisher.publishToDlq(any(), any(), any())).thenAnswer {
                val attempts = dlqAttempts.incrementAndGet()
                if (attempts >= 2) { // Initial + 1 retry = 2 attempts total
                    allAttemptsLatch.countDown()
                }
                false // Always fail to trigger retry
            }

            // Act
            consumerWithConfig.receive("key", message, consumer, partition, offset)

            // Wait for all DLQ attempts
            allAttemptsLatch.await(3, TimeUnit.SECONDS)
            Thread.sleep(100)

            // Assert - Should have attempted at least 2 times (initial + 1 retry)
            verify(dlqPublisher, atLeast(2)).publishToDlq(any(), any(), any())
        }

        @Test
        fun `should handle processing retry boundary condition at maxRetries`() {
            // Arrange
            val message = createTestMessage()
            val partition = 0
            val offset = 100L
            val testConfig = config.copy(maxRetries = 1)
            val consumerWithConfig = TestKafkaConsumer(processor, dlqPublisher, metrics, testConfig, processingExecutor)
            val processingAttempts = AtomicInteger(0)
            val allAttemptsLatch = CountDownLatch(1)

            doAnswer {
                val attempts = processingAttempts.incrementAndGet()
                if (attempts >= 2) { // Initial + 1 retry = 2 attempts total
                    allAttemptsLatch.countDown()
                }
                throw ProcessingException("Processing failed")
            }.whenever(processor).process(any())

            // Act
            consumerWithConfig.receive("key", message, consumer, partition, offset)

            // Wait for all processing attempts
            allAttemptsLatch.await(3, TimeUnit.SECONDS)
            Thread.sleep(100)

            // Assert - Should have attempted exactly 2 times (initial + 1 retry)
            verify(processor, times(2)).process(any())
            verify(metrics).incrementRetry()
            verify(metrics).incrementError("processing")
        }

        @Test
        fun `should handle DLQ exception retry loop boundary condition`() {
            // Arrange
            val message = createTestMessage()
            val partition = 0
            val offset = 100L
            val testConfig = config.copy(dlqMaxRetries = 1, enableDlqFallback = false)
            val consumerWithConfig = TestKafkaConsumer(processor, dlqPublisher, metrics, testConfig, processingExecutor)
            val dlqExceptionAttempts = AtomicInteger(0)
            val allAttemptsLatch = CountDownLatch(1)

            doAnswer { throw ValidationException("Validation failed") }.whenever(processor).process(any())
            whenever(dlqPublisher.publishToDlq(any(), any(), any())).thenAnswer {
                val attempts = dlqExceptionAttempts.incrementAndGet()
                if (attempts >= 2) { // Initial + 1 retry
                    allAttemptsLatch.countDown()
                }
                throw RuntimeException("DLQ publish exception")
            }

            // Act
            consumerWithConfig.receive("key", message, consumer, partition, offset)

            // Wait for all DLQ exception attempts
            allAttemptsLatch.await(3, TimeUnit.SECONDS)
            Thread.sleep(100)

            // Assert - Should have attempted at least 2 times (initial + 1 retry)
            verify(dlqPublisher, atLeast(2)).publishToDlq(any(), any(), any())
            verify(metrics, atLeast(2)).incrementDlqPublishFailure()
        }

        @Test
        fun `should handle DLQ publish failure when dlqAttempt exceeds maxRetries in else block`() {
            // Arrange - This tests the path where dlqAttempt > dlqMaxRetries after incrementing in the else block
            val message = createTestMessage()
            val partition = 0
            val offset = 100L
            val testConfig = config.copy(dlqMaxRetries = 0, enableDlqFallback = false)
            val consumerWithConfig = TestKafkaConsumer(processor, dlqPublisher, metrics, testConfig, processingExecutor)
            val dlqLatch = CountDownLatch(1)

            doAnswer { throw ValidationException("Validation failed") }.whenever(processor).process(any())
            whenever(dlqPublisher.publishToDlq(any(), any(), any())).thenAnswer {
                dlqLatch.countDown()
                false // DLQ publish fails, will increment and check
            }

            // Act
            consumerWithConfig.receive("key", message, consumer, partition, offset)

            // Wait for DLQ attempt
            dlqLatch.await(2, TimeUnit.SECONDS)
            Thread.sleep(100)

            // Assert - Should have attempted DLQ and hit the boundary check
            verify(dlqPublisher, atLeast(1)).publishToDlq(any(), any(), any())
            verify(metrics, atLeast(1)).incrementDlqPublishFailure()
        }

        @Test
        fun `should handle DLQ exception when dlqAttempt exceeds maxRetries in catch block`() {
            // Arrange - This tests the path where dlqAttempt > dlqMaxRetries after incrementing in the catch block
            val message = createTestMessage()
            val partition = 0
            val offset = 100L
            val testConfig = config.copy(dlqMaxRetries = 0, enableDlqFallback = false)
            val consumerWithConfig = TestKafkaConsumer(processor, dlqPublisher, metrics, testConfig, processingExecutor)
            val dlqLatch = CountDownLatch(1)

            doAnswer { throw ValidationException("Validation failed") }.whenever(processor).process(any())
            whenever(dlqPublisher.publishToDlq(any(), any(), any())).thenAnswer {
                dlqLatch.countDown()
                throw RuntimeException("DLQ publish exception")
            }

            // Act
            consumerWithConfig.receive("key", message, consumer, partition, offset)

            // Wait for DLQ exception
            dlqLatch.await(2, TimeUnit.SECONDS)
            Thread.sleep(100)

            // Assert - Should have attempted DLQ and hit the boundary check in catch block
            verify(dlqPublisher, atLeast(1)).publishToDlq(any(), any(), any())
            verify(metrics, atLeast(1)).incrementDlqPublishFailure()
        }

        @Test
        fun `should handle processing exception when attempt exceeds maxRetries`() {
            // Arrange - Test the path where attempt > maxRetries after incrementing
            val message = createTestMessage()
            val partition = 0
            val offset = 100L
            val testConfig = config.copy(maxRetries = 0)
            val consumerWithConfig = TestKafkaConsumer(processor, dlqPublisher, metrics, testConfig, processingExecutor)
            val processingLatch = CountDownLatch(1)

            doAnswer {
                processingLatch.countDown()
                throw ProcessingException("Processing failed")
            }.whenever(processor).process(any())

            // Act
            consumerWithConfig.receive("key", message, consumer, partition, offset)

            // Wait for processing attempt
            processingLatch.await(2, TimeUnit.SECONDS)
            Thread.sleep(100)

            // Assert - Should have attempted processing and hit the boundary check
            verify(processor, atLeast(1)).process(any())
            verify(metrics).incrementError("processing")
        }

        @Test
        fun `should handle generic exception when attempt exceeds maxRetries`() {
            // Arrange - Test the path where attempt > maxRetries after incrementing for generic exception
            val message = createTestMessage()
            val partition = 0
            val offset = 100L
            val testConfig = config.copy(maxRetries = 0)
            val consumerWithConfig = TestKafkaConsumer(processor, dlqPublisher, metrics, testConfig, processingExecutor)
            val processingLatch = CountDownLatch(1)

            doAnswer {
                processingLatch.countDown()
                throw RuntimeException("Unexpected error")
            }.whenever(processor).process(any())

            // Act
            consumerWithConfig.receive("key", message, consumer, partition, offset)

            // Wait for processing attempt
            processingLatch.await(2, TimeUnit.SECONDS)
            Thread.sleep(100)

            // Assert - Should have attempted processing and hit the boundary check
            verify(processor, atLeast(1)).process(any())
            verify(metrics).incrementError("processing")
        }
    }
}
