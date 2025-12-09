package io.github.salomax.neotool.common.graphql

import jakarta.validation.ConstraintViolation
import jakarta.validation.ConstraintViolationException
import jakarta.validation.Validator
import jakarta.validation.constraints.NotBlank
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@DisplayName("InputValidator Tests")
class InputValidatorTest {
    private lateinit var validator: Validator
    private lateinit var inputValidator: InputValidator

    @BeforeEach
    fun setUp() {
        validator = mock()
        inputValidator = InputValidator(validator)
    }

    @Nested
    @DisplayName("validate()")
    inner class ValidateTests {
        @Test
        fun `should not throw when bean is valid`() {
            // Arrange
            val bean = TestBean(value = "valid")
            whenever(validator.validate(bean)).thenReturn(emptySet())

            // Act & Assert - should not throw
            inputValidator.validate(bean)
        }

        @Test
        fun `should throw ConstraintViolationException when bean has violations`() {
            // Arrange
            val bean = TestBean(value = "")
            val violation = mock<ConstraintViolation<TestBean>>()
            whenever(validator.validate(bean)).thenReturn(setOf(violation))

            // Act & Assert
            val exception =
                assertThrows<ConstraintViolationException> {
                    inputValidator.validate(bean)
                }

            assertThat(exception.constraintViolations).hasSize(1)
            assertThat(exception.constraintViolations).contains(violation)
        }

        @Test
        fun `should throw ConstraintViolationException with multiple violations`() {
            // Arrange
            val bean = TestBean(value = "")
            val violation1 = mock<ConstraintViolation<TestBean>>()
            val violation2 = mock<ConstraintViolation<TestBean>>()
            whenever(validator.validate(bean)).thenReturn(setOf(violation1, violation2))

            // Act & Assert
            val exception =
                assertThrows<ConstraintViolationException> {
                    inputValidator.validate(bean)
                }

            assertThat(exception.constraintViolations).hasSize(2)
            assertThat(exception.constraintViolations).contains(violation1, violation2)
        }
    }

    // Test bean for validation
    private data class TestBean(
        @field:NotBlank val value: String,
    )
}
