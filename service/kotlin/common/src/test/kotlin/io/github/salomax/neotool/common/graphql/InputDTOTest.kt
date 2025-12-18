package io.github.salomax.neotool.common.graphql

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("InputDTO Tests")
class InputDTOTest {
    // Test implementation
    private class TestInputDTO : BaseInputDTO()

    @Nested
    @DisplayName("BaseInputDTO")
    inner class BaseInputDTOTests {
        @Test
        fun `should have default validate implementation that does nothing`() {
            // Arrange
            val dto = TestInputDTO()

            // Act & Assert - should not throw
            dto.validate()
        }
    }

    @Nested
    @DisplayName("InputPatterns")
    inner class InputPatternsTests {
        @Test
        fun `should return email regex pattern`() {
            // Act
            val pattern = InputPatterns.email()

            // Assert
            assertThat(pattern).isEqualTo("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")
            assertThat("test@example.com").matches(java.util.regex.Pattern.compile(pattern))
            assertThat("invalid-email").doesNotMatch(java.util.regex.Pattern.compile(pattern))
        }

        @Test
        fun `should return UUID regex pattern`() {
            // Act
            val pattern = InputPatterns.uuid()

            // Assert
            assertThat(pattern).isEqualTo("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
            assertThat("550e8400-e29b-41d4-a716-446655440000").matches(java.util.regex.Pattern.compile(pattern))
            assertThat("not-a-uuid").doesNotMatch(java.util.regex.Pattern.compile(pattern))
        }

        @Test
        fun `should return alphanumeric regex pattern`() {
            // Act
            val pattern = InputPatterns.alphanumeric()

            // Assert
            assertThat(pattern).isEqualTo("^[a-zA-Z0-9]+$")
            assertThat("abc123").matches(java.util.regex.Pattern.compile(pattern))
            assertThat("abc-123").doesNotMatch(java.util.regex.Pattern.compile(pattern))
        }

        @Test
        fun `should return status enum pattern with single value`() {
            // Act
            val pattern = InputPatterns.statusEnum("ACTIVE")

            // Assert
            assertThat(pattern).isEqualTo("ACTIVE")
            assertThat("ACTIVE").matches(java.util.regex.Pattern.compile(pattern))
        }

        @Test
        fun `should return status enum pattern with multiple values`() {
            // Act
            val pattern = InputPatterns.statusEnum("ACTIVE", "INACTIVE", "PENDING")

            // Assert
            assertThat(pattern).isEqualTo("ACTIVE|INACTIVE|PENDING")
        }
    }

    @Nested
    @DisplayName("GraphQLValidations")
    inner class GraphQLValidationsTests {
        @Test
        fun `should create NotBlank annotation`() {
            // Act
            val annotation = GraphQLValidations.notBlank("Custom message")

            // Assert
            assertThat(annotation).isInstanceOf(NotBlank::class.java)
            assertThat(annotation.message).isEqualTo("Custom message")
        }

        @Test
        fun `should create NotBlank annotation with default message`() {
            // Act
            val annotation = GraphQLValidations.notBlank()

            // Assert
            assertThat(annotation).isInstanceOf(NotBlank::class.java)
            assertThat(annotation.message).isEqualTo("Field must not be blank")
        }

        @Test
        fun `should create Email annotation`() {
            // Act
            val annotation = GraphQLValidations.email("Custom email message")

            // Assert
            assertThat(annotation).isInstanceOf(Email::class.java)
            assertThat(annotation.message).isEqualTo("Custom email message")
        }

        @Test
        fun `should create Email annotation with default message`() {
            // Act
            val annotation = GraphQLValidations.email()

            // Assert
            assertThat(annotation).isInstanceOf(Email::class.java)
            assertThat(annotation.message).isEqualTo("Must be a valid email address")
        }

        @Test
        fun `should create Pattern annotation`() {
            // Act
            val annotation = GraphQLValidations.pattern("^[a-z]+$", "Must be lowercase")

            // Assert
            assertThat(annotation).isInstanceOf(Pattern::class.java)
            assertThat(annotation.regexp).isEqualTo("^[a-z]+$")
            assertThat(annotation.message).isEqualTo("Must be lowercase")
        }

        @Test
        fun `should create Min annotation`() {
            // Act
            val annotation = GraphQLValidations.min(10L, "Must be at least 10")

            // Assert
            assertThat(annotation).isInstanceOf(Min::class.java)
            assertThat(annotation.value).isEqualTo(10L)
            assertThat(annotation.message).isEqualTo("Must be at least 10")
        }

        @Test
        fun `should create Min annotation with default message`() {
            // Act
            val annotation = GraphQLValidations.min(5L)

            // Assert
            assertThat(annotation).isInstanceOf(Min::class.java)
            assertThat(annotation.value).isEqualTo(5L)
            assertThat(annotation.message).isEqualTo("Value must be >= 5")
        }

        @Test
        fun `should create Max annotation`() {
            // Act
            val annotation = GraphQLValidations.max(100L, "Must be at most 100")

            // Assert
            assertThat(annotation).isInstanceOf(Max::class.java)
            assertThat(annotation.value).isEqualTo(100L)
            assertThat(annotation.message).isEqualTo("Must be at most 100")
        }

        @Test
        fun `should create Max annotation with default message`() {
            // Act
            val annotation = GraphQLValidations.max(50L)

            // Assert
            assertThat(annotation).isInstanceOf(Max::class.java)
            assertThat(annotation.value).isEqualTo(50L)
            assertThat(annotation.message).isEqualTo("Value must be <= 50")
        }

        @Test
        fun `should create Size annotation`() {
            // Act
            val annotation = GraphQLValidations.size(1, 100, "Size must be between 1 and 100")

            // Assert
            assertThat(annotation).isInstanceOf(Size::class.java)
            assertThat(annotation.min).isEqualTo(1)
            assertThat(annotation.max).isEqualTo(100)
            assertThat(annotation.message).isEqualTo("Size must be between 1 and 100")
        }

        @Test
        fun `should create Size annotation with default message`() {
            // Act
            val annotation = GraphQLValidations.size(2, 50)

            // Assert
            assertThat(annotation).isInstanceOf(Size::class.java)
            assertThat(annotation.min).isEqualTo(2)
            assertThat(annotation.max).isEqualTo(50)
            assertThat(annotation.message).isEqualTo("Size must be between 2 and 50")
        }
    }
}
