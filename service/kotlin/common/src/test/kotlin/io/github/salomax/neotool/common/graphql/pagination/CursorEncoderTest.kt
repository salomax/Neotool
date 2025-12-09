package io.github.salomax.neotool.common.graphql.pagination

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Base64
import java.util.UUID

@DisplayName("CursorEncoder Tests")
class CursorEncoderTest {
    @Nested
    @DisplayName("encodeCursor(UUID)")
    inner class EncodeCursorUuidTests {
        @Test
        fun `should encode UUID to base64 URL-safe string`() {
            // Arrange
            val uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")

            // Act
            val result = CursorEncoder.encodeCursor(uuid)

            // Assert
            assertThat(result).isNotEmpty
            assertThat(result).doesNotContain("+", "/", "=") // URL-safe, no padding
            // Verify it's valid base64
            val decoded = Base64.getUrlDecoder().decode(result)
            val decodedString = String(decoded)
            assertThat(decodedString).isEqualTo(uuid.toString())
        }

        @Test
        fun `should encode different UUIDs to different cursors`() {
            // Arrange
            val uuid1 = UUID.randomUUID()
            val uuid2 = UUID.randomUUID()

            // Act
            val cursor1 = CursorEncoder.encodeCursor(uuid1)
            val cursor2 = CursorEncoder.encodeCursor(uuid2)

            // Assert
            assertThat(cursor1).isNotEqualTo(cursor2)
        }
    }

    @Nested
    @DisplayName("decodeCursorToUuid(String)")
    inner class DecodeCursorToUuidTests {
        @Test
        fun `should decode valid base64 cursor to UUID`() {
            // Arrange
            val uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
            val cursor = CursorEncoder.encodeCursor(uuid)

            // Act
            val result = CursorEncoder.decodeCursorToUuid(cursor)

            // Assert
            assertThat(result).isEqualTo(uuid)
        }

        @Test
        fun `should throw IllegalArgumentException for invalid base64`() {
            // Arrange
            val invalidCursor = "not-valid-base64!!!"

            // Act & Assert
            val exception =
                assertThrows<IllegalArgumentException> {
                    CursorEncoder.decodeCursorToUuid(invalidCursor)
                }
            assertThat(exception.message).contains("Invalid cursor format")
            assertThat(exception.message).contains(invalidCursor)
        }

        @Test
        fun `should throw IllegalArgumentException for base64 that is not a UUID`() {
            // Arrange
            val notUuidString = "not-a-uuid"
            val invalidCursor = Base64.getUrlEncoder().withoutPadding().encodeToString(notUuidString.toByteArray())

            // Act & Assert
            val exception =
                assertThrows<IllegalArgumentException> {
                    CursorEncoder.decodeCursorToUuid(invalidCursor)
                }
            assertThat(exception.message).contains("Invalid cursor format")
        }

        @Test
        fun `should throw IllegalArgumentException for empty string`() {
            // Act & Assert
            val exception =
                assertThrows<IllegalArgumentException> {
                    CursorEncoder.decodeCursorToUuid("")
                }
            assertThat(exception.message).contains("Invalid cursor format")
        }
    }

    @Nested
    @DisplayName("encodeCursor(Int)")
    inner class EncodeCursorIntTests {
        @Test
        fun `should encode Int to base64 URL-safe string`() {
            // Arrange
            val id = 12345

            // Act
            val result = CursorEncoder.encodeCursor(id)

            // Assert
            assertThat(result).isNotEmpty
            assertThat(result).doesNotContain("+", "/", "=") // URL-safe, no padding
            // Verify it's valid base64
            val decoded = Base64.getUrlDecoder().decode(result)
            val decodedString = String(decoded)
            assertThat(decodedString).isEqualTo("12345")
        }

        @Test
        fun `should encode zero to base64`() {
            // Arrange
            val id = 0

            // Act
            val result = CursorEncoder.encodeCursor(id)

            // Assert
            assertThat(result).isNotEmpty
            val decoded = Base64.getUrlDecoder().decode(result)
            val decodedString = String(decoded)
            assertThat(decodedString).isEqualTo("0")
        }

        @Test
        fun `should encode negative Int to base64`() {
            // Arrange
            val id = -12345

            // Act
            val result = CursorEncoder.encodeCursor(id)

            // Assert
            assertThat(result).isNotEmpty
            val decoded = Base64.getUrlDecoder().decode(result)
            val decodedString = String(decoded)
            assertThat(decodedString).isEqualTo("-12345")
        }

        @Test
        fun `should encode different Ints to different cursors`() {
            // Arrange
            val id1 = 100
            val id2 = 200

            // Act
            val cursor1 = CursorEncoder.encodeCursor(id1)
            val cursor2 = CursorEncoder.encodeCursor(id2)

            // Assert
            assertThat(cursor1).isNotEqualTo(cursor2)
        }
    }

    @Nested
    @DisplayName("decodeCursorToInt(String)")
    inner class DecodeCursorToIntTests {
        @Test
        fun `should decode valid base64 cursor to Int`() {
            // Arrange
            val id = 12345
            val cursor = CursorEncoder.encodeCursor(id)

            // Act
            val result = CursorEncoder.decodeCursorToInt(cursor)

            // Assert
            assertThat(result).isEqualTo(id)
        }

        @Test
        fun `should decode zero`() {
            // Arrange
            val id = 0
            val cursor = CursorEncoder.encodeCursor(id)

            // Act
            val result = CursorEncoder.decodeCursorToInt(cursor)

            // Assert
            assertThat(result).isEqualTo(0)
        }

        @Test
        fun `should decode negative Int`() {
            // Arrange
            val id = -12345
            val cursor = CursorEncoder.encodeCursor(id)

            // Act
            val result = CursorEncoder.decodeCursorToInt(cursor)

            // Assert
            assertThat(result).isEqualTo(id)
        }

        @Test
        fun `should throw IllegalArgumentException for invalid base64`() {
            // Arrange
            val invalidCursor = "not-valid-base64!!!"

            // Act & Assert
            val exception =
                assertThrows<IllegalArgumentException> {
                    CursorEncoder.decodeCursorToInt(invalidCursor)
                }
            assertThat(exception.message).contains("Invalid cursor format")
            assertThat(exception.message).contains(invalidCursor)
        }

        @Test
        fun `should throw IllegalArgumentException for base64 that is not an Int`() {
            // Arrange
            val notIntString = "not-an-int"
            val invalidCursor = Base64.getUrlEncoder().withoutPadding().encodeToString(notIntString.toByteArray())

            // Act & Assert
            val exception =
                assertThrows<IllegalArgumentException> {
                    CursorEncoder.decodeCursorToInt(invalidCursor)
                }
            assertThat(exception.message).contains("Invalid cursor format")
        }

        @Test
        fun `should throw IllegalArgumentException for empty string`() {
            // Act & Assert
            val exception =
                assertThrows<IllegalArgumentException> {
                    CursorEncoder.decodeCursorToInt("")
                }
            assertThat(exception.message).contains("Invalid cursor format")
        }
    }

    @Nested
    @DisplayName("decodeCursor(String) - deprecated")
    inner class DecodeCursorDeprecatedTests {
        @Test
        fun `should decode cursor using deprecated method`() {
            // Arrange
            val uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
            val cursor = CursorEncoder.encodeCursor(uuid)

            // Act
            @Suppress("DEPRECATION")
            val result = CursorEncoder.decodeCursor(cursor)

            // Assert
            assertThat(result).isEqualTo(uuid)
        }

        @Test
        fun `should throw IllegalArgumentException for invalid cursor`() {
            // Arrange
            val invalidCursor = "invalid"

            // Act & Assert
            val exception =
                assertThrows<IllegalArgumentException> {
                    @Suppress("DEPRECATION")
                    CursorEncoder.decodeCursor(invalidCursor)
                }
            assertThat(exception.message).contains("Invalid cursor format")
        }
    }

    @Nested
    @DisplayName("encodeCompositeCursor(Map, UUID)")
    inner class EncodeCompositeCursorUuidTests {
        @Test
        fun `should encode composite cursor with UUID id`() {
            // Arrange
            val fieldValues = mapOf("name" to "Test", "age" to 25)
            val id = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")

            // Act
            val result =
                CursorEncoder.encodeCompositeCursor(fieldValues, id)

            // Assert
            assertThat(result).isNotEmpty
            assertThat(result).doesNotContain("+", "/", "=") // URL-safe, no padding
            // Verify it can be decoded
            val decoded = CursorEncoder.decodeCompositeCursorToUuid(result)
            assertThat(decoded.id).isEqualTo(id.toString())
            assertThat(decoded.fieldValues).isEqualTo(fieldValues)
        }

        @Test
        fun `should encode composite cursor with empty field values`() {
            // Arrange
            val fieldValues = emptyMap<String, Any?>()
            val id = UUID.randomUUID()

            // Act
            val result =
                CursorEncoder.encodeCompositeCursor(fieldValues, id)

            // Assert
            assertThat(result).isNotEmpty
            val decoded = CursorEncoder.decodeCompositeCursorToUuid(result)
            assertThat(decoded.id).isEqualTo(id.toString())
            assertThat(decoded.fieldValues).isEmpty()
        }

        @Test
        fun `should encode composite cursor with null field values`() {
            // Arrange
            val fieldValues = mapOf("name" to "Test", "optional" to null)
            val id = UUID.randomUUID()

            // Act
            val result =
                CursorEncoder.encodeCompositeCursor(fieldValues, id)

            // Assert
            assertThat(result).isNotEmpty
            val decoded = CursorEncoder.decodeCompositeCursorToUuid(result)
            assertThat(decoded.id).isEqualTo(id.toString())
            assertThat(decoded.fieldValues["name"]).isEqualTo("Test")
            assertThat(decoded.fieldValues["optional"]).isNull()
        }

        @Test
        fun `should encode composite cursor with multiple field types`() {
            // Arrange
            val fieldValues =
                mapOf(
                    "string" to "value",
                    "int" to 123,
                    "double" to 45.67,
                    "boolean" to true,
                )
            val id = UUID.randomUUID()

            // Act
            val result =
                CursorEncoder.encodeCompositeCursor(fieldValues, id)

            // Assert
            assertThat(result).isNotEmpty
            val decoded = CursorEncoder.decodeCompositeCursorToUuid(result)
            assertThat(decoded.id).isEqualTo(id.toString())
            assertThat(decoded.fieldValues["string"]).isEqualTo("value")
            assertThat(decoded.fieldValues["int"]).isEqualTo(123)
        }
    }

    @Nested
    @DisplayName("encodeCompositeCursor(Map, Int)")
    inner class EncodeCompositeCursorIntTests {
        @Test
        fun `should encode composite cursor with Int id`() {
            // Arrange
            val fieldValues = mapOf("name" to "Test")
            val id = 12345

            // Act
            val result =
                CursorEncoder.encodeCompositeCursor(fieldValues, id)

            // Assert
            assertThat(result).isNotEmpty
            val decoded = CursorEncoder.decodeCompositeCursorToInt(result)
            assertThat(decoded.id).isEqualTo("12345")
            assertThat(decoded.fieldValues).isEqualTo(fieldValues)
        }

        @Test
        fun `should encode composite cursor with zero Int id`() {
            // Arrange
            val fieldValues = emptyMap<String, Any?>()
            val id = 0

            // Act
            val result =
                CursorEncoder.encodeCompositeCursor(fieldValues, id)

            // Assert
            assertThat(result).isNotEmpty
            val decoded = CursorEncoder.decodeCompositeCursorToInt(result)
            assertThat(decoded.id).isEqualTo("0")
        }
    }

    @Nested
    @DisplayName("decodeCompositeCursorToUuid(String)")
    inner class DecodeCompositeCursorToUuidTests {
        @Test
        fun `should decode valid composite cursor with UUID id`() {
            // Arrange
            val fieldValues = mapOf("name" to "Test", "age" to 25)
            val id = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
            val cursor = CursorEncoder.encodeCompositeCursor(fieldValues, id)

            // Act
            val result = CursorEncoder.decodeCompositeCursorToUuid(cursor)

            // Assert
            assertThat(result.id).isEqualTo(id.toString())
            assertThat(result.fieldValues).isEqualTo(fieldValues)
        }

        @Test
        fun `should throw IllegalArgumentException for invalid base64`() {
            // Arrange
            val invalidCursor = "not-valid-base64!!!"

            // Act & Assert
            val exception =
                assertThrows<IllegalArgumentException> {
                    CursorEncoder.decodeCompositeCursorToUuid(invalidCursor)
                }
            assertThat(exception.message).contains("Invalid composite cursor format")
            assertThat(exception.message).contains(invalidCursor)
        }

        @Test
        fun `should throw IllegalArgumentException for invalid JSON`() {
            // Arrange
            val invalidJson = "not-json"
            val invalidCursor = Base64.getUrlEncoder().withoutPadding().encodeToString(invalidJson.toByteArray())

            // Act & Assert
            val exception =
                assertThrows<IllegalArgumentException> {
                    CursorEncoder.decodeCompositeCursorToUuid(invalidCursor)
                }
            assertThat(exception.message).contains("Invalid composite cursor format")
        }

        @Test
        fun `should throw IllegalArgumentException for missing id field`() {
            // Arrange
            val jsonWithoutId = """{"fieldValues":{"name":"Test"}}"""
            val invalidCursor = Base64.getUrlEncoder().withoutPadding().encodeToString(jsonWithoutId.toByteArray())

            // Act & Assert
            val exception =
                assertThrows<IllegalArgumentException> {
                    CursorEncoder.decodeCompositeCursorToUuid(invalidCursor)
                }
            assertThat(exception.message).contains("Missing id in cursor")
        }

        @Test
        fun `should throw IllegalArgumentException for invalid UUID format in id`() {
            // Arrange
            val jsonWithInvalidUuid = """{"fieldValues":{},"id":"not-a-uuid"}"""
            val invalidCursor =
                Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(jsonWithInvalidUuid.toByteArray())

            // Act & Assert
            val exception =
                assertThrows<IllegalArgumentException> {
                    CursorEncoder.decodeCompositeCursorToUuid(invalidCursor)
                }
            assertThat(exception.message).contains("Invalid composite cursor format")
        }

        @Test
        fun `should handle cursor with missing fieldValues`() {
            // Arrange
            val jsonWithoutFieldValues = """{"id":"550e8400-e29b-41d4-a716-446655440000"}"""
            val cursor = Base64.getUrlEncoder().withoutPadding().encodeToString(jsonWithoutFieldValues.toByteArray())

            // Act
            val result = CursorEncoder.decodeCompositeCursorToUuid(cursor)

            // Assert
            assertThat(result.id).isEqualTo("550e8400-e29b-41d4-a716-446655440000")
            assertThat(result.fieldValues).isEmpty()
        }

        @Test
        fun `should throw IllegalArgumentException for empty string`() {
            // Act & Assert
            val exception =
                assertThrows<IllegalArgumentException> {
                    CursorEncoder.decodeCompositeCursorToUuid("")
                }
            assertThat(exception.message).contains("Invalid composite cursor format")
        }
    }

    @Nested
    @DisplayName("decodeCompositeCursorToInt(String)")
    inner class DecodeCompositeCursorToIntTests {
        @Test
        fun `should decode valid composite cursor with Int id`() {
            // Arrange
            val fieldValues = mapOf("name" to "Test")
            val id = 12345
            val cursor = CursorEncoder.encodeCompositeCursor(fieldValues, id)

            // Act
            val result = CursorEncoder.decodeCompositeCursorToInt(cursor)

            // Assert
            assertThat(result.id).isEqualTo("12345")
            assertThat(result.fieldValues).isEqualTo(fieldValues)
        }

        @Test
        fun `should decode zero Int id`() {
            // Arrange
            val fieldValues = emptyMap<String, Any?>()
            val id = 0
            val cursor = CursorEncoder.encodeCompositeCursor(fieldValues, id)

            // Act
            val result = CursorEncoder.decodeCompositeCursorToInt(cursor)

            // Assert
            assertThat(result.id).isEqualTo("0")
        }

        @Test
        fun `should throw IllegalArgumentException for invalid base64`() {
            // Arrange
            val invalidCursor = "not-valid-base64!!!"

            // Act & Assert
            val exception =
                assertThrows<IllegalArgumentException> {
                    CursorEncoder.decodeCompositeCursorToInt(invalidCursor)
                }
            assertThat(exception.message).contains("Invalid composite cursor format")
        }

        @Test
        fun `should throw IllegalArgumentException for invalid JSON`() {
            // Arrange
            val invalidJson = "not-json"
            val invalidCursor = Base64.getUrlEncoder().withoutPadding().encodeToString(invalidJson.toByteArray())

            // Act & Assert
            val exception =
                assertThrows<IllegalArgumentException> {
                    CursorEncoder.decodeCompositeCursorToInt(invalidCursor)
                }
            assertThat(exception.message).contains("Invalid composite cursor format")
        }

        @Test
        fun `should throw IllegalArgumentException for missing id field`() {
            // Arrange
            val jsonWithoutId = """{"fieldValues":{"name":"Test"}}"""
            val invalidCursor = Base64.getUrlEncoder().withoutPadding().encodeToString(jsonWithoutId.toByteArray())

            // Act & Assert
            val exception =
                assertThrows<IllegalArgumentException> {
                    CursorEncoder.decodeCompositeCursorToInt(invalidCursor)
                }
            assertThat(exception.message).contains("Missing id in cursor")
        }

        @Test
        fun `should throw IllegalArgumentException for invalid Int format in id`() {
            // Arrange
            val jsonWithInvalidInt = """{"fieldValues":{},"id":"not-an-int"}"""
            val invalidCursor =
                Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(jsonWithInvalidInt.toByteArray())

            // Act & Assert
            val exception =
                assertThrows<IllegalArgumentException> {
                    CursorEncoder.decodeCompositeCursorToInt(invalidCursor)
                }
            assertThat(exception.message).contains("Invalid composite cursor format")
        }

        @Test
        fun `should handle cursor with missing fieldValues`() {
            // Arrange
            val jsonWithoutFieldValues = """{"id":"12345"}"""
            val cursor = Base64.getUrlEncoder().withoutPadding().encodeToString(jsonWithoutFieldValues.toByteArray())

            // Act
            val result = CursorEncoder.decodeCompositeCursorToInt(cursor)

            // Assert
            assertThat(result.id).isEqualTo("12345")
            assertThat(result.fieldValues).isEmpty()
        }

        @Test
        fun `should throw IllegalArgumentException for empty string`() {
            // Act & Assert
            val exception =
                assertThrows<IllegalArgumentException> {
                    CursorEncoder.decodeCompositeCursorToInt("")
                }
            assertThat(exception.message).contains("Invalid composite cursor format")
        }
    }
}
