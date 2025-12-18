package io.github.salomax.neotool.common.graphql.payload

import jakarta.validation.ConstraintViolation
import jakarta.validation.ConstraintViolationException
import jakarta.validation.Path
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@DisplayName("GraphQLPayload Tests")
class GraphQLPayloadTest {
    @Nested
    @DisplayName("SuccessPayload")
    inner class SuccessPayloadTests {
        @Test
        fun `should create success payload with data`() {
            // Arrange
            val data = "test data"

            // Act
            val payload = SuccessPayload(data = data)

            // Assert
            assertThat(payload.data).isEqualTo(data)
            assertThat(payload.errors).isEmpty()
            assertThat(payload.success).isTrue()
        }

        @Test
        fun `should create success payload with empty errors`() {
            // Arrange
            val data = 123

            // Act
            val payload = SuccessPayload(data = data, errors = emptyList())

            // Assert
            assertThat(payload.data).isEqualTo(123)
            assertThat(payload.errors).isEmpty()
            assertThat(payload.success).isTrue()
        }
    }

    @Nested
    @DisplayName("ErrorPayload")
    inner class ErrorPayloadTests {
        @Test
        fun `should create error payload with errors`() {
            // Arrange
            val errors =
                listOf(
                    GraphQLError(field = listOf("field1"), message = "Error 1"),
                    GraphQLError(field = listOf("field2"), message = "Error 2"),
                )

            // Act
            val payload = ErrorPayload<String>(errors = errors)

            // Assert
            assertThat(payload.data).isNull()
            assertThat(payload.errors).hasSize(2)
            assertThat(payload.success).isFalse()
        }

        @Test
        fun `should create error payload with data and errors`() {
            // Arrange
            val data = "partial data"
            val errors = listOf(GraphQLError(field = listOf("field"), message = "Error"))

            // Act
            val payload = ErrorPayload(data = data, errors = errors)

            // Assert
            assertThat(payload.data).isEqualTo("partial data")
            assertThat(payload.errors).hasSize(1)
            assertThat(payload.success).isFalse()
        }
    }

    @Nested
    @DisplayName("GraphQLError")
    inner class GraphQLErrorTests {
        @Test
        fun `should create GraphQL error with all fields`() {
            // Act
            val error =
                GraphQLError(
                    field = listOf("field1", "field2"),
                    message = "Error message",
                    code = "ERROR_CODE",
                )

            // Assert
            assertThat(error.field).containsExactly("field1", "field2")
            assertThat(error.message).isEqualTo("Error message")
            assertThat(error.code).isEqualTo("ERROR_CODE")
        }

        @Test
        fun `should create GraphQL error without code`() {
            // Act
            val error =
                GraphQLError(
                    field = listOf("field"),
                    message = "Error message",
                )

            // Assert
            assertThat(error.field).containsExactly("field")
            assertThat(error.message).isEqualTo("Error message")
            assertThat(error.code).isNull()
        }
    }

    @Nested
    @DisplayName("GraphQLPayloadFactory")
    inner class GraphQLPayloadFactoryTests {
        @Test
        fun `should create success payload`() {
            // Arrange
            val data = "test data"

            // Act
            val payload = GraphQLPayloadFactory.success(data)

            // Assert
            assertThat(payload).isInstanceOf(SuccessPayload::class.java)
            assertThat(payload.data).isEqualTo("test data")
            assertThat(payload.errors).isEmpty()
            assertThat(payload.success).isTrue()
        }

        @Test
        fun `should create error payload from ConstraintViolationException`() {
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

            val exception = jakarta.validation.ConstraintViolationException(setOf(violation1, violation2))

            // Act
            val payload = GraphQLPayloadFactory.error<String>(exception)

            // Assert
            assertThat(payload).isInstanceOf(ErrorPayload::class.java)
            assertThat(payload.success).isFalse()
            assertThat(payload.errors).hasSize(2)

            // Check that both errors are present (order may vary due to Set iteration)
            val field1Error = payload.errors.find { it.field.contains("field1") }
            val field2Error = payload.errors.find { it.field.contains("field2") }

            assertThat(field1Error).isNotNull
            assertThat(field1Error!!.field).containsExactly("field1")
            assertThat(field1Error.message).isEqualTo("Error 1")
            assertThat(field1Error.code).isEqualTo("VALIDATION_ERROR")

            assertThat(field2Error).isNotNull
            assertThat(field2Error!!.field).containsExactly("field2")
            assertThat(field2Error.message).isEqualTo("Error 2")
            assertThat(field2Error.code).isEqualTo("VALIDATION_ERROR")
        }

        @Test
        fun `should create error payload from IllegalArgumentException`() {
            // Arrange
            val exception = IllegalArgumentException("Invalid input")

            // Act
            val payload = GraphQLPayloadFactory.error<String>(exception)

            // Assert
            assertThat(payload).isInstanceOf(ErrorPayload::class.java)
            assertThat(payload.success).isFalse()
            assertThat(payload.errors).hasSize(1)
            assertThat(payload.errors[0].field).containsExactly("input")
            assertThat(payload.errors[0].message).isEqualTo("Invalid input")
            assertThat(payload.errors[0].code).isEqualTo("INVALID_INPUT")
        }

        @Test
        fun `should create error payload from IllegalArgumentException with null message`() {
            // Arrange
            val exception = IllegalArgumentException()

            // Act
            val payload = GraphQLPayloadFactory.error<String>(exception)

            // Assert
            assertThat(payload).isInstanceOf(ErrorPayload::class.java)
            assertThat(payload.success).isFalse()
            assertThat(payload.errors).hasSize(1)
            assertThat(payload.errors[0].field).containsExactly("input")
            assertThat(payload.errors[0].message).isEqualTo("Invalid input")
            assertThat(payload.errors[0].code).isEqualTo("INVALID_INPUT")
        }

        @Test
        fun `should create error payload from NoSuchElementException`() {
            // Arrange
            val exception = NoSuchElementException("Not found")

            // Act
            val payload = GraphQLPayloadFactory.error<String>(exception)

            // Assert
            assertThat(payload).isInstanceOf(ErrorPayload::class.java)
            assertThat(payload.success).isFalse()
            assertThat(payload.errors).hasSize(1)
            assertThat(payload.errors[0].field).containsExactly("id")
            assertThat(payload.errors[0].message).isEqualTo("Resource not found")
            assertThat(payload.errors[0].code).isEqualTo("NOT_FOUND")
        }

        @Test
        fun `should create error payload from generic Exception`() {
            // Arrange
            val exception = RuntimeException("Unexpected error")

            // Act
            val payload = GraphQLPayloadFactory.error<String>(exception)

            // Assert
            assertThat(payload).isInstanceOf(ErrorPayload::class.java)
            assertThat(payload.success).isFalse()
            assertThat(payload.errors).hasSize(1)
            assertThat(payload.errors[0].field).containsExactly("general")
            assertThat(payload.errors[0].message).isEqualTo("Unexpected error")
            assertThat(payload.errors[0].code).isEqualTo("INTERNAL_ERROR")
        }

        @Test
        fun `should create error payload from generic Exception with null message`() {
            // Arrange
            val exception = RuntimeException()

            // Act
            val payload = GraphQLPayloadFactory.error<String>(exception)

            // Assert
            assertThat(payload).isInstanceOf(ErrorPayload::class.java)
            assertThat(payload.success).isFalse()
            assertThat(payload.errors).hasSize(1)
            assertThat(payload.errors[0].field).containsExactly("general")
            assertThat(payload.errors[0].message).isEqualTo("An unexpected error occurred")
            assertThat(payload.errors[0].code).isEqualTo("INTERNAL_ERROR")
        }

        @Test
        fun `should create error payload from list of errors`() {
            // Arrange
            val errors =
                listOf(
                    GraphQLError(field = listOf("field1"), message = "Error 1"),
                    GraphQLError(field = listOf("field2"), message = "Error 2"),
                )

            // Act
            val payload = GraphQLPayloadFactory.error<String>(errors)

            // Assert
            assertThat(payload).isInstanceOf(ErrorPayload::class.java)
            assertThat(payload.success).isFalse()
            assertThat(payload.errors).hasSize(2)
            assertThat(payload.errors).isEqualTo(errors)
        }

        @Test
        fun `should create error payload with single error`() {
            // Act
            val payload =
                GraphQLPayloadFactory.error<String>(
                    field = "field1",
                    message = "Error message",
                    code = "CUSTOM_CODE",
                )

            // Assert
            assertThat(payload).isInstanceOf(ErrorPayload::class.java)
            assertThat(payload.success).isFalse()
            assertThat(payload.errors).hasSize(1)
            assertThat(payload.errors[0].field).containsExactly("field1")
            assertThat(payload.errors[0].message).isEqualTo("Error message")
            assertThat(payload.errors[0].code).isEqualTo("CUSTOM_CODE")
        }

        @Test
        fun `should create error payload with single error without code`() {
            // Act
            val payload =
                GraphQLPayloadFactory.error<String>(
                    field = "field1",
                    message = "Error message",
                )

            // Assert
            assertThat(payload).isInstanceOf(ErrorPayload::class.java)
            assertThat(payload.success).isFalse()
            assertThat(payload.errors).hasSize(1)
            assertThat(payload.errors[0].field).containsExactly("field1")
            assertThat(payload.errors[0].message).isEqualTo("Error message")
            assertThat(payload.errors[0].code).isNull()
        }
    }
}
