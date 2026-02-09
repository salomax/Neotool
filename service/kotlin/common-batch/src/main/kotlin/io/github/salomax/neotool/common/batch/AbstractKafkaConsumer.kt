package io.github.salomax.neotool.common.batch

import io.github.salomax.neotool.common.batch.exceptions.PermanentProcessingException
import io.github.salomax.neotool.common.batch.exceptions.ProcessingException
import io.github.salomax.neotool.common.batch.exceptions.ValidationException
import io.micronaut.configuration.kafka.annotation.KafkaKey
import io.micronaut.configuration.kafka.annotation.KafkaPartition
import jakarta.annotation.PreDestroy
import jakarta.inject.Inject
import mu.KotlinLogging
import org.apache.kafka.clients.consumer.CommitFailedException
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.pow

/**
 * Abstract base class for Kafka consumers with retry logic, DLQ handling, and metrics.
 * Uses virtual threads (Project Loom) for efficient blocking I/O operations.
 *
 * Messages are processed sequentially per partition, ensuring at-least-once delivery semantics.
 * Offset commits happen on the listener thread after each successful processing or DLQ publish.
 *
 * Virtual threads make blocking operations (Thread.sleep, blocking I/O) efficient by parking
 * instead of blocking platform threads, allowing high concurrency with simple blocking code.
 *
 * @param TMessage The message type
 * @param TProcessor The processor type implementing MessageProcessor
 * @param TDlqPublisher The DLQ publisher type implementing DlqPublisherService
 * @param TMetrics The metrics type implementing ConsumerMetrics
 */
abstract class AbstractKafkaConsumer<
    TMessage,
    TProcessor : MessageProcessor<TMessage>,
    TDlqPublisher : DlqPublisherService<TMessage>,
    TMetrics : ConsumerMetrics,
>(
    @Inject protected val processor: TProcessor,
    @Inject protected val dlqPublisher: TDlqPublisher,
    @Inject protected val metrics: TMetrics,
    @Inject protected val config: ConsumerConfig,
    @Inject private val processingExecutor: ProcessingExecutor,
) {
    protected val logger = KotlinLogging.logger {}

    private val isShuttingDown = AtomicBoolean(false)

    private val pendingCommits = ConcurrentLinkedQueue<PendingCommit>()
    private val inFlightTasks = ConcurrentHashMap.newKeySet<CompletableFuture<PendingCommit?>>()
    private val partitionLocks = ConcurrentHashMap<Int, Any>()
    private val lastConsumer = AtomicReference<Consumer<*, *>?>()

    /**
     * Get the topic name this consumer listens to.
     */
    abstract fun getTopicName(): String

    /**
     * Process a message from Kafka.
     * Processes messages sequentially per partition using virtual threads.
     * Commits offsets on the listener thread after successful processing or DLQ publish.
     *
     * Virtual threads handle blocking operations efficiently, so we can use simple blocking code.
     *
     * @param key The record key
     * @param message The message
     * @param consumer The Kafka consumer for manual commit (must be used on listener thread)
     * @param partition The partition number
     * @param offset The message offset
     */
    open fun receive(
        @KafkaKey key: String,
        message: TMessage,
        consumer: Consumer<*, *>,
        @KafkaPartition partition: Int,
        offset: Long,
    ) {
        lastConsumer.set(consumer)
        processPendingCommits(consumer)

        if (isShuttingDown.get()) {
            logger.warn {
                "Shutdown in progress, rejecting new message: " +
                    "recordId=${processor.getRecordId(message)}, partition=$partition, offset=$offset"
            }
            pausePartition(consumer, partition)
            return
        }

        val processingTask =
            processingExecutor.submit<PendingCommit?>(
                {
                    val lock = partitionLocks.computeIfAbsent(partition) { Any() }
                    synchronized(lock) {
                        processMessage(message, partition, offset)
                    }
                },
            )

        inFlightTasks.add(processingTask)

        processingTask.whenComplete { commit: PendingCommit?, throwable: Throwable? ->
            inFlightTasks.remove(processingTask)
            if (throwable != null) {
                handleUnexpectedFailure(message, partition, offset, throwable)
                return@whenComplete
            }

            if (commit != null) {
                pendingCommits.offer(commit)
                // Note: No need to call wakeup() - processPendingCommits() is called
                // at the start of each receive() call, so commits will be processed
                // when the next message arrives. Calling wakeup() from a background
                // thread can interrupt Micronaut's listener poll loop and cause
                // the consumer to stop processing messages.
            }
        }
    }

    /**
     * Gracefully shutdown the consumer by:
     * 1. Setting shutdown flag to reject new messages
     * 2. Pausing all active partitions
     * 3. Draining in-flight work (waiting for current messages to finish processing)
     *
     * PROBLEM: Previously, shutdown() just set a flag and paused partitions, but didn't wait
     * for in-flight messages to complete. If the app stopped while a record was mid-retry,
     * the consumer could exit without committing, causing duplicates or unprocessed records.
     * Thread.sleep loops in retries couldn't be interrupted, leading to incomplete shutdowns.
     *
     * SOLUTION: Track in-flight work per partition, pause partitions, then wait for all
     * in-flight messages to complete (with timeout). This ensures graceful draining before close.
     *
     * Virtual threads are managed by the JVM, so no explicit cleanup is needed.
     *
     * This method is called automatically by Micronaut when the application context is destroyed.
     */
    @PreDestroy
    fun shutdown() {
        if (isShuttingDown.getAndSet(true)) {
            // Already shutting down
            return
        }

        logger.info { "Starting graceful shutdown of Kafka consumer for topic: ${getTopicName()}" }

        pauseAllAssignedPartitions()
        awaitInFlightTasks()
        finalCommitFlush()

        logger.info { "Graceful shutdown completed for Kafka consumer: ${getTopicName()}" }
    }

    private fun pausePartition(
        consumer: Consumer<*, *>,
        partition: Int,
    ) {
        try {
            val topicPartition = TopicPartition(getTopicName(), partition)
            consumer.pause(setOf(topicPartition))
        } catch (e: Exception) {
            logger.warn(e) { "Failed to pause partition $partition" }
        }
    }

    /**
     * Handle DLQ publishing with retry logic using virtual threads.
     * Blocking I/O operations are efficient with virtual threads as they park instead of blocking.
     *
     * @param message The message to publish
     * @param error The error that occurred
     * @param retryCount Number of retries attempted
     * @param recordId The record ID for logging
     * @return true if successfully published, false otherwise
     */
    private fun handleDlqPublish(
        message: TMessage,
        error: Throwable,
        retryCount: Int,
        recordId: String,
    ): Boolean {
        var dlqAttempt = 0

        // Try initial attempt + dlqMaxRetries retries = (dlqMaxRetries + 1) total attempts
        while (dlqAttempt <= config.dlqMaxRetries) {
            if (isShuttingDown.get()) {
                logger.warn {
                    "Shutdown in progress, aborting DLQ publish for recordId=$recordId " +
                        "after $dlqAttempt attempt(s)"
                }
                return false
            }

            try {
                // Blocking I/O is fine - virtual thread parks during I/O
                val success = dlqPublisher.publishToDlq(message, error, retryCount)

                if (success) {
                    metrics.incrementDlq()
                    metrics.incrementError("dlq")
                    logger.info {
                        "Successfully published message to DLQ: recordId=$recordId"
                    }
                    return true
                } else {
                    metrics.incrementDlqPublishFailure()
                    dlqAttempt++

                    if (dlqAttempt > config.dlqMaxRetries) {
                        logger.error {
                            "Failed to publish message to DLQ after ${config.dlqMaxRetries} " +
                                "attempts: recordId=$recordId. Message will be retried by Kafka."
                        }

                        // If DLQ fallback is enabled, try alternative storage
                        if (config.enableDlqFallback) {
                            return handleDlqFallback(message, error, retryCount, recordId)
                        }

                        return false
                    }

                    logger.warn {
                        "DLQ publish returned false for recordId=$recordId, " +
                            "attempt $dlqAttempt/${config.dlqMaxRetries}"
                    }
                    val delayMs = calculateRetryDelay(dlqAttempt)
                    if (isShuttingDown.get()) {
                        logger.warn {
                            "Shutdown detected before DLQ retry delay for recordId=$recordId"
                        }
                        return false
                    }
                    Thread.sleep(delayMs)
                    if (isShuttingDown.get()) {
                        logger.warn {
                            "Shutdown detected after DLQ retry delay for recordId=$recordId"
                        }
                        return false
                    }
                }
            } catch (e: Exception) {
                metrics.incrementDlqPublishFailure()
                dlqAttempt++

                if (dlqAttempt > config.dlqMaxRetries) {
                    logger.error(e) {
                        "Failed to publish message to DLQ after ${config.dlqMaxRetries} " +
                            "attempts: recordId=$recordId. Message will be retried by Kafka."
                    }

                    // If DLQ fallback is enabled, try alternative storage
                    if (config.enableDlqFallback) {
                        return handleDlqFallback(message, error, retryCount, recordId)
                    }

                    return false
                }

                logger.warn(e) {
                    "DLQ publish failed for recordId=$recordId, " +
                        "attempt $dlqAttempt/${config.dlqMaxRetries}: ${e.message}"
                }
                val delayMs = calculateRetryDelay(dlqAttempt)
                if (isShuttingDown.get()) {
                    logger.warn {
                        "Shutdown detected before DLQ retry delay for recordId=$recordId"
                    }
                    return false
                }
                Thread.sleep(delayMs)
                if (isShuttingDown.get()) {
                    logger.warn {
                        "Shutdown detected after DLQ retry delay for recordId=$recordId"
                    }
                    return false
                }
            }
        }

        return false
    }

    /**
     * Handle DLQ fallback when primary DLQ publishing fails.
     * Override this method to implement custom fallback logic (e.g., local file, database, etc.).
     *
     * @param message The message to store
     * @param error The error that occurred
     * @param retryCount Number of retries attempted
     * @param recordId The record ID for logging
     * @return true if successfully stored, false otherwise
     */
    protected open fun handleDlqFallback(
        message: TMessage,
        error: Throwable,
        retryCount: Int,
        recordId: String,
    ): Boolean {
        logger.error {
            "DLQ fallback not implemented for recordId=$recordId. " +
                "Message will be retried by Kafka."
        }
        // TODO: Implement fallback storage (e.g., local file, database, etc.)
        return false
    }

    /**
     * Commit offset after successful processing or DLQ publishing.
     * Handles CommitFailedException separately to avoid false DLQ entries.
     *
     * @param consumer The Kafka consumer
     * @param topicPartition The topic partition
     * @param offsetAndMetadata The offset to commit
     * @param recordId The record ID for logging
     * @param isProcessingSuccess Whether processing was successful (vs DLQ publish)
     */
    private fun commitOffset(
        consumer: Consumer<*, *>,
        topicPartition: TopicPartition,
        offsetAndMetadata: OffsetAndMetadata,
        recordId: String,
        isProcessingSuccess: Boolean,
    ) {
        try {
            consumer.commitSync(
                mapOf(topicPartition to offsetAndMetadata),
                Duration.ofSeconds(config.commitTimeoutSeconds),
            )

            logger.debug {
                "Committed offset: partition=${topicPartition.partition()}, " +
                    "offset=${offsetAndMetadata.offset() - 1}"
            }
        } catch (e: CommitFailedException) {
            // Commit failure is separate from processing failure
            // Don't send to DLQ if processing was successful
            logger.error(e) {
                "Commit failed for recordId=$recordId. " +
                    if (isProcessingSuccess) {
                        "Processing was successful but commit failed. Will retry commit."
                    } else {
                        "DLQ publish was successful but commit failed. Will retry commit."
                    }
            }

            // Retry commit once before giving up
            try {
                Thread.sleep(100) // Brief delay before retry
                consumer.commitSync(
                    mapOf(topicPartition to offsetAndMetadata),
                    Duration.ofSeconds(config.commitTimeoutSeconds),
                )
                logger.info {
                    "Successfully committed offset on retry for recordId=$recordId"
                }
            } catch (retryException: Exception) {
                logger.error(retryException) {
                    "Commit retry also failed for recordId=$recordId. " +
                        "Message will be reprocessed by Kafka."
                }
                // Don't throw - let Kafka handle reprocessing
            }
        } catch (e: Exception) {
            logger.error(e) {
                "Unexpected error committing offset for recordId=$recordId"
            }
            // Don't throw - let Kafka handle reprocessing
        }
    }

    /**
     * Calculate retry delay with exponential backoff.
     *
     * @param attempt The current attempt number (1-based)
     * @return Delay in milliseconds
     */
    private fun calculateRetryDelay(attempt: Int): Long {
        val delay =
            (
                config.initialRetryDelayMs *
                    config.retryBackoffMultiplier.pow(attempt - 1)
            ).toLong()
        return minOf(delay, config.maxRetryDelayMs)
    }

    private fun processMessage(
        message: TMessage,
        partition: Int,
        offset: Long,
    ): PendingCommit? {
        val recordId = processor.getRecordId(message)
        val topicPartition = TopicPartition(getTopicName(), partition)
        val result = processWithRetry(message, recordId)

        return when (result) {
            is ProcessingResult.Success ->
                PendingCommit(
                    topicPartition,
                    OffsetAndMetadata(offset + 1),
                    recordId,
                    isProcessingSuccess = true,
                )
            is ProcessingResult.Failed -> {
                if (result.alreadyPublishedToDlq) {
                    PendingCommit(
                        topicPartition,
                        OffsetAndMetadata(offset + 1),
                        recordId,
                        isProcessingSuccess = false,
                    )
                } else {
                    val dlqSuccess =
                        handleDlqPublish(
                            message,
                            result.lastError ?: ProcessingException("All retries exhausted"),
                            result.retryCount,
                            recordId,
                        )
                    if (dlqSuccess) {
                        PendingCommit(
                            topicPartition,
                            OffsetAndMetadata(offset + 1),
                            recordId,
                            isProcessingSuccess = false,
                        )
                    } else {
                        logger.error {
                            "DLQ publish failed for recordId=$recordId. " +
                                "Message will be retried by Kafka."
                        }
                        null
                    }
                }
            }
        }
    }

    private fun processWithRetry(
        message: TMessage,
        recordId: String,
    ): ProcessingResult {
        var attempt = 0
        var lastError: Throwable? = null

        while (attempt <= config.maxRetries) {
            if (isShuttingDown.get()) {
                logger.warn {
                    "Shutdown in progress, aborting retry loop for recordId=$recordId " +
                        "at attempt=$attempt"
                }
                return ProcessingResult.Failed(
                    alreadyPublishedToDlq = false,
                    retryCount = attempt,
                    lastError = lastError,
                )
            }

            try {
                if (attempt > 0) {
                    metrics.incrementRetry()
                    val delayMs = calculateRetryDelay(attempt)
                    logger.info {
                        "Retrying processing: recordId=$recordId, " +
                            "attempt=$attempt/${config.maxRetries}, delay=${delayMs}ms"
                    }

                    if (isShuttingDown.get()) {
                        logger.warn {
                            "Shutdown detected before retry delay for recordId=$recordId"
                        }
                        return ProcessingResult.Failed(
                            alreadyPublishedToDlq = false,
                            retryCount = attempt,
                            lastError = lastError,
                        )
                    }
                    Thread.sleep(delayMs)
                    if (isShuttingDown.get()) {
                        logger.warn {
                            "Shutdown detected after retry delay for recordId=$recordId"
                        }
                        return ProcessingResult.Failed(
                            alreadyPublishedToDlq = false,
                            retryCount = attempt,
                            lastError = lastError,
                        )
                    }
                }

                processor.process(message)
                return ProcessingResult.Success
            } catch (e: ValidationException) {
                lastError = e
                logger.error(e) {
                    "Validation error for recordId=$recordId: ${e.message}. " +
                        "Sending to DLQ without retry."
                }
                metrics.incrementError("validation")

                val dlqSuccess = handleDlqPublish(message, e, attempt, recordId)
                return ProcessingResult.Failed(
                    alreadyPublishedToDlq = dlqSuccess,
                    retryCount = attempt,
                    lastError = e,
                )
            } catch (e: PermanentProcessingException) {
                lastError = e
                logger.error(e) {
                    "Permanent processing error for recordId=$recordId: ${e.message}. " +
                        "Sending to DLQ without retry."
                }
                metrics.incrementError("processing_permanent")

                val dlqSuccess = handleDlqPublish(message, e, attempt, recordId)
                return ProcessingResult.Failed(
                    alreadyPublishedToDlq = dlqSuccess,
                    retryCount = attempt,
                    lastError = e,
                )
            } catch (e: ProcessingException) {
                lastError = e
                attempt++
                if (attempt > config.maxRetries) {
                    logger.error(e) {
                        "All retries exhausted for recordId=$recordId " +
                            "after ${config.maxRetries} attempts"
                    }
                    metrics.incrementError("processing")
                    return ProcessingResult.Failed(
                        alreadyPublishedToDlq = false,
                        retryCount = config.maxRetries,
                        lastError = e,
                    )
                }
                logger.warn(e) {
                    "Processing failed for recordId=$recordId, " +
                        "attempt $attempt/${config.maxRetries}: ${e.message}"
                }
            } catch (e: Exception) {
                lastError = e
                attempt++
                if (attempt > config.maxRetries) {
                    logger.error(e) {
                        "Unexpected error for recordId=$recordId " +
                            "after ${config.maxRetries} attempts"
                    }
                    metrics.incrementError("processing")
                    return ProcessingResult.Failed(
                        alreadyPublishedToDlq = false,
                        retryCount = config.maxRetries,
                        lastError = e,
                    )
                }
                logger.warn(e) {
                    "Unexpected error for recordId=$recordId, " +
                        "attempt $attempt/${config.maxRetries}: ${e.message}"
                }
            }
        }

        return ProcessingResult.Failed(
            alreadyPublishedToDlq = false,
            retryCount = config.maxRetries,
            lastError = lastError,
        )
    }

    private fun pauseAllAssignedPartitions() {
        val consumer = lastConsumer.get()
        if (consumer == null) {
            logger.debug {
                "No consumer instance available to pause partitions during shutdown"
            }
            return
        }

        try {
            val assignments = consumer.assignment()
            if (assignments.isEmpty()) {
                logger.debug { "No assigned partitions to pause during shutdown" }
                return
            }
            consumer.pause(assignments)
            logger.info { "Paused ${assignments.size} partition(s) during shutdown" }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to pause partitions during shutdown" }
        }
    }

    private fun processPendingCommits(consumer: Consumer<*, *>) {
        var commit = pendingCommits.poll()
        while (commit != null) {
            commitOffset(
                consumer,
                commit.topicPartition,
                commit.offsetAndMetadata,
                commit.recordId,
                commit.isProcessingSuccess,
            )
            commit = pendingCommits.poll()
        }
    }

    private fun awaitInFlightTasks() {
        val futures = inFlightTasks.toTypedArray()
        if (futures.isEmpty()) {
            return
        }

        logger.info { "Waiting for ${futures.size} in-flight task(s) to finish" }
        val timeoutMs = config.shutdownTimeoutSeconds * 1000L
        val combined = CompletableFuture.allOf(*futures)
        try {
            combined.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            logger.warn(e) {
                "Timeout while waiting for in-flight tasks. Some work may be reprocessed."
            }
        }
    }

    private fun finalCommitFlush() {
        val consumer = lastConsumer.get() ?: return
        processPendingCommits(consumer)
    }

    private fun handleUnexpectedFailure(
        message: TMessage,
        partition: Int,
        offset: Long,
        throwable: Throwable,
    ) {
        val recordId = processor.getRecordId(message)
        logger.error(throwable) {
            "Unexpected error processing message off-thread: recordId=$recordId, " +
                "partition=$partition, offset=$offset"
        }
        metrics.incrementError()

        val dlqSuccess = handleDlqPublish(message, throwable, 0, recordId)
        if (dlqSuccess) {
            val topicPartition = TopicPartition(getTopicName(), partition)
            pendingCommits.offer(
                PendingCommit(
                    topicPartition,
                    OffsetAndMetadata(offset + 1),
                    recordId,
                    isProcessingSuccess = false,
                ),
            )
        }
    }

    private data class PendingCommit(
        val topicPartition: TopicPartition,
        val offsetAndMetadata: OffsetAndMetadata,
        val recordId: String,
        val isProcessingSuccess: Boolean,
    )
}
