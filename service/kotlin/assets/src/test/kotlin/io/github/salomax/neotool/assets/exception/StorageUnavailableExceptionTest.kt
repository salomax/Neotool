package io.github.salomax.neotool.assets.exception

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("StorageUnavailableException Unit Tests")
class StorageUnavailableExceptionTest {
    @Test
    fun `should create exception with message`() {
        // Arrange
        val message = "Storage service unavailable"

        // Act
        val exception = StorageUnavailableException(message)

        // Assert
        assertThat(exception.message).isEqualTo(message)
        assertThat(exception.cause).isNull()
    }

    @Test
    fun `should create exception with message and cause`() {
        // Arrange
        val message = "Storage service unavailable"
        val cause = RuntimeException("Underlying error")

        // Act
        val exception = StorageUnavailableException(message, cause)

        // Assert
        assertThat(exception.message).isEqualTo(message)
        assertThat(exception.cause).isEqualTo(cause)
    }

    @Test
    fun `should create exception with null cause`() {
        // Arrange
        val message = "Storage service unavailable"

        // Act
        val exception = StorageUnavailableException(message, null)

        // Assert
        assertThat(exception.message).isEqualTo(message)
        assertThat(exception.cause).isNull()
    }
}
