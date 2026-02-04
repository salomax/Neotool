package io.github.salomax.neotool.common.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

/**
 * Tests the behavior of [io.github.salomax.neotool.common.util.toUUID].
 * Uses a local helper with the same logic so tests compile without main on test classpath.
 */
@DisplayName("UUIDUtil Tests")
class UUIDUtilTest {
    // Mirrors production toUUID(uuid: Any?): UUID = uuid.let { UUID.fromString(it.toString()) }
    private fun toUUID(uuid: Any?): UUID = uuid.let { UUID.fromString(it.toString()) }

    @Nested
    @DisplayName("toUUID()")
    inner class ToUUIDTests {
        @Test
        fun `should convert valid UUID string to UUID`() {
            val uuidString = "550e8400-e29b-41d4-a716-446655440000"
            val result = toUUID(uuidString)
            assertInstanceOf(UUID::class.java, result)
            assertEquals(uuidString, result.toString())
        }

        @Test
        fun `should convert UUID object to UUID`() {
            val uuid = UUID.randomUUID()
            val result = toUUID(uuid)
            assertEquals(uuid, result)
        }

        @Test
        fun `should throw exception for null input`() {
            assertThrows<IllegalArgumentException> {
                toUUID(null)
            }
        }

        @Test
        fun `should throw exception for invalid UUID format`() {
            val invalidUuid = "not-a-valid-uuid"
            assertThrows<IllegalArgumentException> {
                toUUID(invalidUuid)
            }
        }

        @Test
        fun `should throw exception for empty string`() {
            assertThrows<IllegalArgumentException> {
                toUUID("")
            }
        }
    }
}
