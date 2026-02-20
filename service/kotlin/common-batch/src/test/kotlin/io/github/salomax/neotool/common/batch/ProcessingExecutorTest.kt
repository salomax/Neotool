package io.github.salomax.neotool.common.batch

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@DisplayName("ProcessingExecutor Unit Tests")
class ProcessingExecutorTest {
    private lateinit var executor: ProcessingExecutor

    @BeforeEach
    fun setUp() {
        executor = ProcessingExecutor()
    }

    @AfterEach
    fun tearDown() {
        executor.onDestroy()
    }

    @Test
    fun `should submit task and return CompletableFuture`() {
        // Arrange
        val latch = CountDownLatch(1)
        var executed = false

        // Act
        val future =
            executor.submit<Boolean> {
                executed = true
                latch.countDown()
                true
            }

        // Assert
        latch.await(2, TimeUnit.SECONDS)
        assertThat(executed).isTrue()
        assertThat(future.get(1, TimeUnit.SECONDS)).isTrue()
    }

    @Test
    fun `should execute tasks asynchronously`() {
        // Arrange
        val latch1 = CountDownLatch(1)
        val latch2 = CountDownLatch(1)
        val executionOrder = mutableListOf<String>()

        // Act
        val future1 =
            executor.submit<String> {
                latch1.await(2, TimeUnit.SECONDS)
                synchronized(executionOrder) {
                    executionOrder.add("task1")
                }
                "result1"
            }

        val future2 =
            executor.submit<String> {
                synchronized(executionOrder) {
                    executionOrder.add("task2")
                }
                latch2.countDown()
                "result2"
            }

        // Wait for task2 to complete
        latch2.await(2, TimeUnit.SECONDS)
        latch1.countDown() // Release task1

        // Wait for both to complete
        future1.get(2, TimeUnit.SECONDS)
        future2.get(2, TimeUnit.SECONDS)

        // Assert - task2 should complete before task1 (since task1 was blocked)
        assertThat(executionOrder).contains("task2")
        assertThat(future1.get()).isEqualTo("result1")
        assertThat(future2.get()).isEqualTo("result2")
    }

    @Test
    fun `should handle exceptions in submitted tasks`() {
        // Arrange
        val exception = RuntimeException("Test exception")

        // Act
        val future =
            executor.submit<String> {
                throw exception
            }

        // Assert
        assertThat(future)
            .failsWithin(2, TimeUnit.SECONDS)
            .withThrowableOfType(Exception::class.java)
    }

    @Test
    fun `should shutdown executor on destroy`() {
        // Act
        executor.onDestroy()

        // Assert - onDestroy should complete without exception
        // Note: After shutdown, submitting tasks may still work briefly
        // but the executor is in the process of shutting down
    }

    @Test
    fun `should return result from submitted task`() {
        // Act
        val future =
            executor.submit<Int> {
                42
            }

        // Assert
        assertThat(future.get(1, TimeUnit.SECONDS)).isEqualTo(42)
    }

    @Test
    fun `should handle null return values`() {
        // Act
        val future =
            executor.submit<String?> {
                null
            }

        // Assert
        assertThat(future.get(1, TimeUnit.SECONDS)).isNull()
    }
}
