package io.github.salomax.neotool.common.exception

import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import jakarta.persistence.OptimisticLockException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.postgresql.util.PSQLException
import jakarta.validation.ConstraintViolationException as ValidationConstraintViolationException
import org.hibernate.exception.ConstraintViolationException as HibernateConstraintViolationException

@DisplayName("DataIntegrityHandler Tests")
class DataIntegrityHandlerTest {
    private lateinit var request: HttpRequest<*>
    private lateinit var dataAccessExceptionHandler: DataAccessExceptionHandler
    private lateinit var optimisticLockHandler: OptimisticLockHandler
    private lateinit var constraintViolationHandler: ConstraintViolationHandler
    private lateinit var validationExceptionHandler: ValidationExceptionHandler
    private lateinit var illegalArgumentExceptionHandler: IllegalArgumentExceptionHandler
    private lateinit var staleObjectHandler: StaleObjectHandler

    @BeforeEach
    fun setUp() {
        request = mock()
        dataAccessExceptionHandler = DataAccessExceptionHandler()
        optimisticLockHandler = OptimisticLockHandler()
        constraintViolationHandler = ConstraintViolationHandler()
        validationExceptionHandler = ValidationExceptionHandler()
        illegalArgumentExceptionHandler = IllegalArgumentExceptionHandler()
        staleObjectHandler = StaleObjectHandler()
    }

    @Nested
    @DisplayName("DataAccessHandler")
    inner class DataAccessHandlerTests {
        @Test
        fun `should return CONFLICT for UNIQUE constraint violation`() {
            // Arrange
            val psqlException = mock<PSQLException>()
            org.mockito.kotlin.whenever(psqlException.sqlState).thenReturn("23505")
            val hibernateException = mock<HibernateConstraintViolationException>()
            org.mockito.kotlin.whenever(hibernateException.sqlState).thenReturn("23505")
            org.mockito.kotlin.whenever(hibernateException.cause).thenReturn(psqlException)
            val exception = DataAccessException("Data access error", hibernateException)

            // Act
            val response = dataAccessExceptionHandler.handle(request, exception)

            // Assert
            assertThat(response.status()).isEqualTo(HttpStatus.CONFLICT)
            assertThat(response.body().message).isEqualTo("Duplicate value")
        }

        @Test
        fun `should return CONFLICT for UNIQUE constraint violation with PSQLException directly`() {
            // Arrange
            val psqlException = mock<PSQLException>()
            org.mockito.kotlin.whenever(psqlException.sqlState).thenReturn("23505")
            val exception = DataAccessException("Data access error", psqlException)

            // Act
            val response = dataAccessExceptionHandler.handle(request, exception)

            // Assert
            assertThat(response.status()).isEqualTo(HttpStatus.CONFLICT)
            assertThat(response.body().message).isEqualTo("Duplicate value")
        }

        @Test
        fun `should return CONFLICT for foreign key constraint violation`() {
            // Arrange
            val psqlException = mock<PSQLException>()
            org.mockito.kotlin.whenever(psqlException.sqlState).thenReturn("23503")
            val hibernateException = mock<HibernateConstraintViolationException>()
            org.mockito.kotlin.whenever(hibernateException.sqlState).thenReturn("23503")
            org.mockito.kotlin.whenever(hibernateException.cause).thenReturn(psqlException)
            val exception = DataAccessException("Data access error", hibernateException)

            // Act
            val response = dataAccessExceptionHandler.handle(request, exception)

            // Assert
            assertThat(response.status()).isEqualTo(HttpStatus.CONFLICT)
            assertThat(response.body().message).isEqualTo("Foreign key violation")
        }

        @Test
        fun `should return BAD_REQUEST for NOT NULL constraint violation`() {
            // Arrange
            val psqlException = mock<PSQLException>()
            org.mockito.kotlin.whenever(psqlException.sqlState).thenReturn("23502")
            val exception = DataAccessException("Data access error", psqlException)

            // Act
            val response = dataAccessExceptionHandler.handle(request, exception)

            // Assert
            assertThat(response.status()).isEqualTo(HttpStatus.BAD_REQUEST)
            assertThat(response.body().message).isEqualTo("Null not allowed")
        }

        @Test
        fun `should return BAD_REQUEST for generic DataAccessException`() {
            // Arrange
            val exception = DataAccessException("Generic data access error")

            // Act
            val response = dataAccessExceptionHandler.handle(request, exception)

            // Assert
            assertThat(response.status()).isEqualTo(HttpStatus.BAD_REQUEST)
            assertThat(response.body().message).isEqualTo("Data integrity violation")
        }

        @Test
        fun `should return BAD_REQUEST for DataAccessException with unknown cause`() {
            // Arrange
            val cause = RuntimeException("Unknown error")
            val exception = DataAccessException("Data access error", cause)

            // Act
            val response = dataAccessExceptionHandler.handle(request, exception)

            // Assert
            assertThat(response.status()).isEqualTo(HttpStatus.BAD_REQUEST)
            assertThat(response.body().message).isEqualTo("Data integrity violation")
        }
    }

    @Nested
    @DisplayName("OptimisticLockHandler")
    inner class OptimisticLockHandlerTests {
        @Test
        fun `should return CONFLICT for OptimisticLockException`() {
            // Arrange
            val exception = OptimisticLockException()

            // Act
            val response = optimisticLockHandler.handle(request, exception)

            // Assert
            assertThat(response.status()).isEqualTo(HttpStatus.CONFLICT)
            assertThat(
                response.body().message,
            ).isEqualTo("The resource was modified by another process. Please reload and retry.")
        }
    }

    @Nested
    @DisplayName("ConstraintViolationHandler")
    inner class ConstraintViolationHandlerTests {
        @Test
        fun `should return CONFLICT for ConstraintViolationException`() {
            // Arrange
            val exception = mock<HibernateConstraintViolationException>()
            org.mockito.kotlin.whenever(exception.sqlState).thenReturn("23505")

            // Act
            val response = constraintViolationHandler.handle(request, exception)

            // Assert
            assertThat(response.status()).isEqualTo(HttpStatus.CONFLICT)
            assertThat(response.body().message).isEqualTo("Duplicate key value violates unique constraint.")
        }
    }

    @Nested
    @DisplayName("ValidationExceptionHandler")
    inner class ValidationExceptionHandlerTests {
        @Test
        fun `should return BAD_REQUEST with violation messages`() {
            // Arrange
            val violation1 = mock<jakarta.validation.ConstraintViolation<Any>>()
            val violation2 = mock<jakarta.validation.ConstraintViolation<Any>>()
            val path1 = mock<jakarta.validation.Path>()
            val path2 = mock<jakarta.validation.Path>()

            org.mockito.kotlin.whenever(violation1.propertyPath).thenReturn(path1)
            org.mockito.kotlin.whenever(path1.toString()).thenReturn("field1")
            org.mockito.kotlin.whenever(violation1.message).thenReturn("Error 1")

            org.mockito.kotlin.whenever(violation2.propertyPath).thenReturn(path2)
            org.mockito.kotlin.whenever(path2.toString()).thenReturn("field2")
            org.mockito.kotlin.whenever(violation2.message).thenReturn("Error 2")

            val exception = ValidationConstraintViolationException(setOf(violation1, violation2))

            // Act
            val response = validationExceptionHandler.handle(request, exception)

            // Assert
            assertThat(response.status()).isEqualTo(HttpStatus.BAD_REQUEST)
            assertThat(response.body().message).contains("Validation failed:")
            assertThat(response.body().message).contains("field1: Error 1")
            assertThat(response.body().message).contains("field2: Error 2")
        }

        @Test
        fun `should return BAD_REQUEST with single violation message`() {
            // Arrange
            val violation = mock<jakarta.validation.ConstraintViolation<Any>>()
            val path = mock<jakarta.validation.Path>()

            org.mockito.kotlin.whenever(violation.propertyPath).thenReturn(path)
            org.mockito.kotlin.whenever(path.toString()).thenReturn("email")
            org.mockito.kotlin.whenever(violation.message).thenReturn("Must be a valid email")

            val exception = ValidationConstraintViolationException(setOf(violation))

            // Act
            val response = validationExceptionHandler.handle(request, exception)

            // Assert
            assertThat(response.status()).isEqualTo(HttpStatus.BAD_REQUEST)
            assertThat(response.body().message).isEqualTo("Validation failed: email: Must be a valid email")
        }
    }

    @Nested
    @DisplayName("IllegalArgumentExceptionHandler")
    inner class IllegalArgumentExceptionHandlerTests {
        @Test
        fun `should return BAD_REQUEST with exception message`() {
            // Arrange
            val exception = IllegalArgumentException("Invalid input parameter")

            // Act
            val response = illegalArgumentExceptionHandler.handle(request, exception)

            // Assert
            assertThat(response.status()).isEqualTo(HttpStatus.BAD_REQUEST)
            assertThat(response.body().message).isEqualTo("Invalid input: Invalid input parameter")
        }

        @Test
        fun `should return BAD_REQUEST with null message`() {
            // Arrange
            val exception = IllegalArgumentException()

            // Act
            val response = illegalArgumentExceptionHandler.handle(request, exception)

            // Assert
            assertThat(response.status()).isEqualTo(HttpStatus.BAD_REQUEST)
            assertThat(response.body().message).isEqualTo("Invalid input: null")
        }
    }

    @Nested
    @DisplayName("StaleObjectHandler")
    inner class StaleObjectHandlerTests {
        @Test
        fun `should return CONFLICT for StaleObjectStateException`() {
            // Arrange
            val exception = org.hibernate.StaleObjectStateException("Entity", 1L)

            // Act
            val response = staleObjectHandler.handle(request, exception)

            // Assert
            assertThat(response.status()).isEqualTo(HttpStatus.CONFLICT)
            assertThat(
                response.body().message,
            ).isEqualTo("The resource was modified by another process. Please reload and retry.")
        }
    }
}
