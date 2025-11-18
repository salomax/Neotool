package io.github.salomax.neotool.example.test.unit.graphql

import io.github.salomax.neotool.common.graphql.payload.GraphQLPayloadFactory
import jakarta.validation.ConstraintViolation
import jakarta.validation.ConstraintViolationException
import jakarta.validation.Path
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.*

@DisplayName("GraphQLPayloadFactory Unit Tests")
class GraphQLPayloadFactoryTest {

    @Nested
    @DisplayName("Success Payload")
    inner class SuccessPayloadTests {

        @Test
        fun `should create success payload with data`() {
            val data = mapOf("id" to "123", "name" to "Test")

            val payload = GraphQLPayloadFactory.success(data)

            assertThat(payload.success).isTrue()
            assertThat(payload.data).isEqualTo(data)
            assertThat(payload.errors).isEmpty()
        }
    }

    @Nested
    @DisplayName("Error Payload from Exception")
    inner class ErrorPayloadFromExceptionTests {

        @Test
        fun `should create error payload from ConstraintViolationException`() {
            val violation1 = createMockViolation("name", "Name is required")
            val violation2 = createMockViolation("email", "Email is invalid")
            val exception = ConstraintViolationException(setOf(violation1, violation2))

            val payload = GraphQLPayloadFactory.error<Map<String, Any>>(exception)

            assertThat(payload.success).isFalse()
            assertThat(payload.data).isNull()
            assertThat(payload.errors).hasSize(2)
            
            // Check that both errors are present (order may vary)
            val nameError = payload.errors.find { it.field.contains("name") }
            val emailError = payload.errors.find { it.field.contains("email") }
            
            assertThat(nameError).isNotNull
            assertThat(nameError!!.message).isEqualTo("Name is required")
            assertThat(nameError.code).isEqualTo("VALIDATION_ERROR")
            
            assertThat(emailError).isNotNull
            assertThat(emailError!!.message).isEqualTo("Email is invalid")
            assertThat(emailError.code).isEqualTo("VALIDATION_ERROR")
        }

        @Test
        fun `should create error payload from IllegalArgumentException`() {
            val exception = IllegalArgumentException("Invalid input parameter")

            val payload = GraphQLPayloadFactory.error<Map<String, Any>>(exception)

            assertThat(payload.success).isFalse()
            assertThat(payload.data).isNull()
            assertThat(payload.errors).hasSize(1)
            assertThat(payload.errors[0].field).contains("input")
            assertThat(payload.errors[0].message).isEqualTo("Invalid input parameter")
            assertThat(payload.errors[0].code).isEqualTo("INVALID_INPUT")
        }

        @Test
        fun `should create error payload from IllegalArgumentException with null message`() {
            val exception = IllegalArgumentException(null as String?)

            val payload = GraphQLPayloadFactory.error<Map<String, Any>>(exception)

            assertThat(payload.success).isFalse()
            assertThat(payload.data).isNull()
            assertThat(payload.errors).hasSize(1)
            assertThat(payload.errors[0].field).contains("input")
            assertThat(payload.errors[0].message).isEqualTo("Invalid input")
            assertThat(payload.errors[0].code).isEqualTo("INVALID_INPUT")
        }

        @Test
        fun `should create error payload from NoSuchElementException`() {
            val exception = NoSuchElementException("Element not found")

            val payload = GraphQLPayloadFactory.error<Map<String, Any>>(exception)

            assertThat(payload.success).isFalse()
            assertThat(payload.data).isNull()
            assertThat(payload.errors).hasSize(1)
            assertThat(payload.errors[0].field).contains("id")
            assertThat(payload.errors[0].message).isEqualTo("Resource not found")
            assertThat(payload.errors[0].code).isEqualTo("NOT_FOUND")
        }

        @Test
        fun `should create error payload from generic Exception`() {
            val exception = RuntimeException("Unexpected error occurred")

            val payload = GraphQLPayloadFactory.error<Map<String, Any>>(exception)

            assertThat(payload.success).isFalse()
            assertThat(payload.data).isNull()
            assertThat(payload.errors).hasSize(1)
            assertThat(payload.errors[0].field).contains("general")
            assertThat(payload.errors[0].message).isEqualTo("Unexpected error occurred")
            assertThat(payload.errors[0].code).isEqualTo("INTERNAL_ERROR")
        }

        @Test
        fun `should create error payload from generic Exception with null message`() {
            val exception = RuntimeException(null as String?)

            val payload = GraphQLPayloadFactory.error<Map<String, Any>>(exception)

            assertThat(payload.success).isFalse()
            assertThat(payload.data).isNull()
            assertThat(payload.errors).hasSize(1)
            assertThat(payload.errors[0].field).contains("general")
            assertThat(payload.errors[0].message).isEqualTo("An unexpected error occurred")
            assertThat(payload.errors[0].code).isEqualTo("INTERNAL_ERROR")
        }
    }

    @Nested
    @DisplayName("Error Payload with Custom Errors")
    inner class ErrorPayloadWithCustomErrorsTests {

        @Test
        fun `should create error payload with custom errors list`() {
            val errors = listOf(
                io.github.salomax.neotool.common.graphql.payload.GraphQLError(
                    field = listOf("field1"),
                    message = "Error 1",
                    code = "ERROR_1"
                ),
                io.github.salomax.neotool.common.graphql.payload.GraphQLError(
                    field = listOf("field2"),
                    message = "Error 2",
                    code = "ERROR_2"
                )
            )

            val payload = GraphQLPayloadFactory.error<Map<String, Any>>(errors)

            assertThat(payload.success).isFalse()
            assertThat(payload.data).isNull()
            assertThat(payload.errors).hasSize(2)
            assertThat(payload.errors).isEqualTo(errors)
        }

        @Test
        fun `should create error payload with single error`() {
            val payload = GraphQLPayloadFactory.error<Map<String, Any>>(
                field = "fieldName",
                message = "Custom error message",
                code = "CUSTOM_ERROR"
            )

            assertThat(payload.success).isFalse()
            assertThat(payload.data).isNull()
            assertThat(payload.errors).hasSize(1)
            assertThat(payload.errors[0].field).contains("fieldName")
            assertThat(payload.errors[0].message).isEqualTo("Custom error message")
            assertThat(payload.errors[0].code).isEqualTo("CUSTOM_ERROR")
        }

        @Test
        fun `should create error payload with single error without code`() {
            val payload = GraphQLPayloadFactory.error<Map<String, Any>>(
                field = "fieldName",
                message = "Custom error message",
                code = null
            )

            assertThat(payload.success).isFalse()
            assertThat(payload.data).isNull()
            assertThat(payload.errors).hasSize(1)
            assertThat(payload.errors[0].field).contains("fieldName")
            assertThat(payload.errors[0].message).isEqualTo("Custom error message")
            assertThat(payload.errors[0].code).isNull()
        }
    }

    private fun createMockViolation(propertyPath: String, message: String): ConstraintViolation<*> {
        val violation = mock<ConstraintViolation<*>>()
        val path = mock<Path>()
        whenever(violation.message).thenReturn(message)
        whenever(violation.messageTemplate).thenReturn(message)
        whenever(violation.propertyPath).thenReturn(path)
        whenever(path.toString()).thenReturn(propertyPath)
        return violation
    }
}

