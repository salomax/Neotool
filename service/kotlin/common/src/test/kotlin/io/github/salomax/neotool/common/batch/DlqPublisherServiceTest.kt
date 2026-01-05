package io.github.salomax.neotool.common.batch

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("DlqPublisherService Interface Tests")
class DlqPublisherServiceTest {
    @Test
    fun `should use default retryCount parameter of zero`() {
        // Arrange
        val service =
            object : DlqPublisherService<String> {
                var capturedRetryCount: Int? = null

                override fun publishToDlq(
                    message: String,
                    error: Throwable,
                    retryCount: Int,
                ): Boolean {
                    capturedRetryCount = retryCount
                    return true
                }

                override fun getDlqTopic(): String = "test-dlq"
            }

        // Act - Call without retryCount (should use default)
        val result = service.publishToDlq("test-message", RuntimeException("test error"))

        // Assert
        assertThat(result).isTrue()
        assertThat(service.capturedRetryCount).isEqualTo(0) // Default value
    }

    @Test
    fun `should use provided retryCount parameter`() {
        // Arrange
        val service =
            object : DlqPublisherService<String> {
                var capturedRetryCount: Int? = null

                override fun publishToDlq(
                    message: String,
                    error: Throwable,
                    retryCount: Int,
                ): Boolean {
                    capturedRetryCount = retryCount
                    return true
                }

                override fun getDlqTopic(): String = "test-dlq"
            }

        // Act - Call with explicit retryCount
        val result = service.publishToDlq("test-message", RuntimeException("test error"), 5)

        // Assert
        assertThat(result).isTrue()
        assertThat(service.capturedRetryCount).isEqualTo(5)
    }
}
