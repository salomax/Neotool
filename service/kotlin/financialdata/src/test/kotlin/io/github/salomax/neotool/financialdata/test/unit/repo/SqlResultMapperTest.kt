package io.github.salomax.neotool.financialdata.test.unit.repo

import io.github.salomax.neotool.financialdata.repo.SqlResultMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID

class SqlResultMapperTest {
    @Test
    fun `getValue returns value at correct index`() {
        val row = arrayOf<Any?>("v")
        val mapper = SqlResultMapper(row, mapOf("c" to 0))

        assertThat(mapper.getValue("c")).isEqualTo("v")
    }

    @Test
    fun `getValue throws when column name not found`() {
        val mapper = SqlResultMapper(arrayOf("v"), emptyMap())

        val ex =
            assertThrows(IllegalArgumentException::class.java) {
                mapper.getValue("x")
            }
        assertThat(ex.message).contains("Column 'x' not found")
    }

    @Test
    fun `getString returns string value`() {
        val mapper = SqlResultMapper(arrayOf<Any?>("s"), mapOf("c" to 0))

        assertThat(mapper.getString("c")).isEqualTo("s")
    }

    @Test
    fun `getString returns null for null value`() {
        val mapper = SqlResultMapper(arrayOf<Any?>(null), mapOf("c" to 0))

        assertThat(mapper.getString("c")).isNull()
    }

    @Test
    fun `getChar returns character value`() {
        val ch = java.lang.Character.valueOf('x')
        val mapper = SqlResultMapper(arrayOf<Any?>(ch), mapOf("c" to 0))

        assertThat(mapper.getChar("c")).isEqualTo(ch)
    }

    @Test
    fun `getNumber returns BigDecimal for Number value`() {
        val mapper = SqlResultMapper(arrayOf<Any?>(1.5), mapOf("c" to 0))

        assertThat(mapper.getNumber("c")).isEqualByComparingTo(BigDecimal("1.5"))
    }

    @Test
    fun `getInt returns Int for Number value`() {
        val mapper = SqlResultMapper(arrayOf<Any?>(42), mapOf("c" to 0))

        assertThat(mapper.getInt("c")).isEqualTo(42)
    }

    @Test
    fun `getLong returns Long for Number value`() {
        val mapper = SqlResultMapper(arrayOf<Any?>(42L), mapOf("c" to 0))

        assertThat(mapper.getLong("c")).isEqualTo(42L)
    }

    @Test
    fun `getBoolean returns Boolean value`() {
        val mapper = SqlResultMapper(arrayOf<Any?>(true), mapOf("c" to 0))

        assertThat(mapper.getBoolean("c")).isTrue()
    }

    @Test
    fun `getUUID returns UUID from UUID object`() {
        val uuid = UUID.randomUUID()
        val mapper = SqlResultMapper(arrayOf<Any?>(uuid), mapOf("c" to 0))

        assertThat(mapper.getUUID("c")).isEqualTo(uuid)
    }

    @Test
    fun `getUUID returns UUID from String representation`() {
        val str = "123e4567-e89b-12d3-a456-426614174000"
        val mapper = SqlResultMapper(arrayOf<Any?>(str), mapOf("c" to 0))

        assertThat(mapper.getUUID("c")).isEqualTo(UUID.fromString(str))
    }

    @Test
    fun `getUUID returns null for invalid UUID string`() {
        val mapper = SqlResultMapper(arrayOf<Any?>("invalid"), mapOf("c" to 0))

        assertThat(mapper.getUUID("c")).isNull()
    }
}
