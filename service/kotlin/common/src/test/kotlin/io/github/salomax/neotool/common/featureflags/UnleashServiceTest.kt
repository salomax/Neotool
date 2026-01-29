package io.github.salomax.neotool.common.featureflags

import io.getunleash.Unleash
import io.getunleash.UnleashContext
import io.getunleash.Variant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock

@DisplayName("UnleashService Unit Tests")
class UnleashServiceTest {
    @Nested
    @DisplayName("Missing Configuration")
    inner class MissingConfigurationTests {
        @Test
        fun `should return false when unleash is not configured`() {
            val service = UnleashService(null, null, null, null, 15)

            val enabled = service.isEnabled("some-flag")

            assertThat(enabled).isFalse
        }

        @Test
        fun `should return disabled variant when unleash is not configured`() {
            val service = UnleashService(null, null, null, null, 15)

            val variant = service.getVariant("some-flag")

            assertThat(variant).isNotNull
            assertThat(variant?.name).isEqualTo("disabled")
            assertThat(variant?.isEnabled).isFalse
        }
    }

    @Nested
    @DisplayName("Error Handling")
    inner class ErrorHandlingTests {
        @Test
        fun `should return false when unleash throws on isEnabled`() {
            val mockUnleash =
                mock<Unleash> {
                    on { isEnabled(any<String>(), any<UnleashContext>()) } doThrow RuntimeException("boom")
                }
            val service = UnleashService("http://example", "token", "app", null, 15)
            injectUnleash(service, mockUnleash)

            val enabled = service.isEnabled("some-flag")

            assertThat(enabled).isFalse
        }

        @Test
        fun `should return null when unleash throws on getVariant`() {
            val mockUnleash =
                mock<Unleash> {
                    on { getVariant(any<String>(), any<UnleashContext>()) } doThrow RuntimeException("boom")
                }
            val service = UnleashService("http://example", "token", "app", null, 15)
            injectUnleash(service, mockUnleash)

            val variant = service.getVariant("some-flag")

            assertThat(variant).isNull()
        }
    }

    @Nested
    @DisplayName("Configured Unleash")
    inner class ConfiguredUnleashTests {
        @Test
        fun `should return value from unleash isEnabled`() {
            val mockUnleash =
                mock<Unleash> {
                    on { isEnabled(any<String>(), any<UnleashContext>()) } doReturn true
                }
            val service = UnleashService("http://example", "token", "app", null, 15)
            injectUnleash(service, mockUnleash)

            val enabled = service.isEnabled("some-flag")

            assertThat(enabled).isTrue
        }

        @Test
        fun `should return variant from unleash`() {
            val expected =
                mock<Variant> {
                    on { name } doReturn "blue"
                    on { isEnabled } doReturn true
                }
            val mockUnleash =
                mock<Unleash> {
                    on { getVariant(any<String>(), any<UnleashContext>()) } doReturn expected
                }
            val service = UnleashService("http://example", "token", "app", null, 15)
            injectUnleash(service, mockUnleash)

            val variant = service.getVariant("some-flag")

            assertThat(variant).isSameAs(expected)
        }
    }

    private fun injectUnleash(
        service: UnleashService,
        unleash: Unleash,
    ) {
        val field =
            UnleashService::class.java.getDeclaredField(
                "unleash\$delegate",
            )
        field.isAccessible = true
        field.set(service, lazyOf(unleash))
    }
}
