package io.github.salomax.neotool.common.util

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

@DisplayName("UUIDUtil Tests")
class UUIDUtilTest {
    @Nested
    @DisplayName("toUUID()")
    inner class ToUUIDTests {
        @Test
        fun `should convert valid UUID string to UUID`() {
            // Arrange
            val uuidString = "550e8400-e29b-41d4-a716-446655440000"

            // Act
            val result = toUUID(uuidString)

            // Assert
            assertThat(result).isInstanceOf(UUID::class.java)
            assertThat(result.toString()).isEqualTo(uuidString)
        }

        @Test
        fun `should convert UUID object to UUID`() {
            // Arrange
            val uuid = UUID.randomUUID()

            // Act
            val result = toUUID(uuid)

            // Assert
            assertThat(result).isEqualTo(uuid)
        }

        @Test
        fun `should throw exception for null input`() {
            // Act & Assert
            // toUUID uses .let which will throw IllegalArgumentException when trying to convert null
            assertThrows<IllegalArgumentException> {
                toUUID(null)
            }
        }

        @Test
        fun `should throw exception for invalid UUID format`() {
            // Arrange
            val invalidUuid = "not-a-valid-uuid"

            // Act & Assert
            assertThrows<IllegalArgumentException> {
                toUUID(invalidUuid)
            }
        }

        @Test
        fun `should throw exception for empty string`() {
            // Act & Assert
            assertThrows<IllegalArgumentException> {
                toUUID("")
            }
        }
    }
}
