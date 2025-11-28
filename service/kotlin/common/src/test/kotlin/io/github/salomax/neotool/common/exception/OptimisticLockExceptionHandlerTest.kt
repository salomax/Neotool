package io.github.salomax.neotool.common.exception

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.StaleObjectStateException
import org.hibernate.StaleStateException
import org.hibernate.dialect.lock.OptimisticEntityLockException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

@DisplayName("OptimisticLockExceptionHandler Tests")
class OptimisticLockExceptionHandlerTest {
    private lateinit var request: HttpRequest<*>
    private lateinit var handler: OptimisticLockExceptionHandler

    @BeforeEach
    fun setUp() {
        request = mock()
        handler = OptimisticLockExceptionHandler()
    }

    @Nested
    @DisplayName("handle()")
    inner class HandleTests {
        @Test
        fun `should return CONFLICT for StaleObjectStateException`() {
            // Arrange
            val exception = StaleObjectStateException("Entity", 1L)

            // Act
            val response = handler.handle(request, exception)

            // Assert
            assertThat(response.status()).isEqualTo(HttpStatus.CONFLICT)
            assertThat(response.body()).isInstanceOf(Map::class.java)
            val body = response.body() as Map<*, *>
            assertThat(body["error"]).isEqualTo("OPTIMISTIC_LOCK_CONFLICT")
            assertThat(
                body["message"],
            ).isEqualTo("The entity was modified by another user. Please refresh and try again.")
        }

        @Test
        fun `should return CONFLICT for StaleStateException`() {
            // Arrange
            val exception = StaleStateException("Entity was modified")

            // Act
            val response = handler.handle(request, exception)

            // Assert
            assertThat(response.status()).isEqualTo(HttpStatus.CONFLICT)
            val body = response.body() as Map<*, *>
            assertThat(body["error"]).isEqualTo("OPTIMISTIC_LOCK_CONFLICT")
            assertThat(
                body["message"],
            ).isEqualTo("The entity was modified by another user. Please refresh and try again.")
        }

        @Test
        fun `should return CONFLICT for OptimisticEntityLockException`() {
            // Arrange
            val exception = OptimisticEntityLockException("Entity was modified", null, null)

            // Act
            val response = handler.handle(request, exception)

            // Assert
            assertThat(response.status()).isEqualTo(HttpStatus.CONFLICT)
            val body = response.body() as Map<*, *>
            assertThat(body["error"]).isEqualTo("OPTIMISTIC_LOCK_CONFLICT")
            assertThat(
                body["message"],
            ).isEqualTo("The entity was modified by another user. Please refresh and try again.")
        }

        @Test
        fun `should include exception message in details`() {
            // Arrange
            val exception = StaleObjectStateException("Entity", 1L)

            // Act
            val response = handler.handle(request, exception)

            // Assert
            val body = response.body() as Map<*, *>
            assertThat(body["details"]).isNotNull()
        }

        @Test
        fun `should handle exception with null message`() {
            // Arrange
            val exception =
                object : StaleStateException("") {
                    override val message: String?
                        get() = null
                }

            // Act
            val response = handler.handle(request, exception)

            // Assert
            assertThat(response.status()).isEqualTo(HttpStatus.CONFLICT)
            val body = response.body() as Map<*, *>
            assertThat(body["details"]).isEqualTo("Unknown error")
        }

        @Test
        fun `should return INTERNAL_SERVER_ERROR for non-optimistic-lock exception`() {
            // Arrange
            val exception = RuntimeException("Not an optimistic lock exception")

            // Act
            val response = handler.handle(request, exception)

            // Assert
            assertThat(response.status()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
            val body = response.body() as Map<*, *>
            assertThat(body["error"]).isEqualTo("INTERNAL_ERROR")
            assertThat(body["message"]).isEqualTo("An unexpected error occurred")
            assertThat(body["details"]).isEqualTo("Not an optimistic lock exception")
        }

        @Test
        fun `should handle non-optimistic-lock exception with null message`() {
            // Arrange
            val exception = RuntimeException()

            // Act
            val response = handler.handle(request, exception)

            // Assert
            assertThat(response.status()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
            val body = response.body() as Map<*, *>
            assertThat(body["error"]).isEqualTo("INTERNAL_ERROR")
            assertThat(body["message"]).isEqualTo("An unexpected error occurred")
            assertThat(body["details"]).isEqualTo("Unknown error")
        }
    }
}
