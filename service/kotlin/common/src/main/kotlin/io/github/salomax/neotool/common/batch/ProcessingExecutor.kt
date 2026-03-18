package io.github.salomax.neotool.common.batch

import jakarta.annotation.PreDestroy
import jakarta.inject.Singleton
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Executor backed by virtual threads for offloading Kafka message processing.
 * Allows the consumer listener thread to return to polling immediately while
 * work continues on lightweight virtual threads.
 */
@Singleton
class ProcessingExecutor {
    private val executor: ExecutorService = Executors.newVirtualThreadPerTaskExecutor()

    /**
     * Submit work to run on a virtual thread.
     */
    fun <T> submit(task: () -> T): CompletableFuture<T> {
        return CompletableFuture.supplyAsync(task, executor)
    }

    @PreDestroy
    fun onDestroy() {
        executor.shutdownNow()
    }
}
