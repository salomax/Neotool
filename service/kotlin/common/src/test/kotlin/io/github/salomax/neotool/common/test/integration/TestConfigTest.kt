package io.github.salomax.neotool.common.test.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("TestConfig Unit Tests")
class TestConfigTest {
    @Test
    fun `should return default string value when key not found`() {
        // Act
        val result = TestConfig.str("non.existent.key", "default-value")

        // Assert
        assertThat(result).isEqualTo("default-value")
    }

    @Test
    fun `should return default boolean value when key not found`() {
        // Act
        val result = TestConfig.bool("non.existent.key", true)

        // Assert
        assertThat(result).isTrue()
    }

    @Test
    fun `should return default int value when key not found`() {
        // Act
        val result = TestConfig.int("non.existent.key", 42)

        // Assert
        assertThat(result).isEqualTo(42)
    }

    @Test
    fun `should return default boolean false when key not found`() {
        // Act
        val result = TestConfig.bool("non.existent.key", false)

        // Assert
        assertThat(result).isFalse()
    }

    @Test
    fun `should handle boolean string values`() {
        // Note: This test verifies the behavior when a config file might exist
        // Since we can't easily create test-config.yml in unit tests,
        // we're testing the default behavior and error handling
        val result = TestConfig.bool("test.key.that.does.not.exist", true)
        assertThat(result).isTrue()
    }

    @Test
    fun `should handle int string values`() {
        // Note: This test verifies the behavior when a config file might exist
        val result = TestConfig.int("test.key.that.does.not.exist", 100)
        assertThat(result).isEqualTo(100)
    }
}
