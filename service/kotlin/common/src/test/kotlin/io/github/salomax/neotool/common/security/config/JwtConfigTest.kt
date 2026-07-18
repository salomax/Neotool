package io.github.salomax.neotool.common.security.config

import io.micronaut.context.ApplicationContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class JwtConfigTest {
    @Test
    fun `should reject missing issuer`() {
        val config = JwtConfig(issuer = "")

        val exception =
            assertThrows<IllegalArgumentException> {
                config.validateIssuer(activeEnvironmentNames = setOf("prod"))
            }

        assertThat(exception.message).contains("JWT issuer must be configured")
    }

    @Test
    fun `should accept absolute https issuer`() {
        val config = JwtConfig(issuer = "https://security.invistus.com.br")

        config.validateIssuer(activeEnvironmentNames = setOf("prod"))
    }

    @Test
    fun `should reject non absolute issuer`() {
        val config = JwtConfig(issuer = "neotool-security-service")

        val exception =
            assertThrows<IllegalArgumentException> {
                config.validateIssuer(activeEnvironmentNames = setOf("prod"))
            }

        assertThat(exception.message).contains("JWT issuer must be an absolute HTTPS URL")
    }

    @Test
    fun `should fail fast during bean creation when issuer is missing`() {
        val exception =
            assertThrows<RuntimeException> {
                ApplicationContext.run(
                    mapOf("jwt.issuer" to ""),
                    "prod",
                ).use { context ->
                    context.getBean(JwtConfig::class.java)
                }
            }

        assertThat(exception).hasMessageContaining("JWT issuer must be configured")
    }

    @Test
    fun `should fail fast during bean creation when issuer is malformed`() {
        val exception =
            assertThrows<RuntimeException> {
                ApplicationContext.run(
                    mapOf("jwt.issuer" to "neotool-security-service"),
                    "prod",
                ).use { context ->
                    context.getBean(JwtConfig::class.java)
                }
            }

        assertThat(exception).hasMessageContaining("JWT issuer must be an absolute HTTPS URL")
    }

    @Test
    fun `should reject non https issuer outside local and test profiles`() {
        val config = JwtConfig(issuer = "http://localhost:8080")

        val exception =
            assertThrows<IllegalArgumentException> {
                config.validateIssuer(activeEnvironmentNames = setOf("prod"))
            }

        assertThat(exception.message).contains("JWT issuer must be an absolute HTTPS URL")
    }

    @Test
    fun `should accept localhost issuer in local profile`() {
        val config = JwtConfig(issuer = "http://localhost:8080")

        config.validateIssuer(activeEnvironmentNames = setOf("local"))
    }

    @Test
    fun `should accept loopback issuer in test profile`() {
        val config = JwtConfig(issuer = "http://127.0.0.1:8080")

        config.validateIssuer(activeEnvironmentNames = setOf("test"))
    }

    @Test
    fun `should allow localhost issuer during bean creation in test profile`() {
        ApplicationContext.run(
            mapOf("jwt.issuer" to "http://localhost:8080"),
            "test",
        ).use { context ->
            assertThat(context.getBean(JwtConfig::class.java).issuer).isEqualTo("http://localhost:8080")
        }
    }

    @Test
    fun `should reject non localhost http issuer in test profile`() {
        val config = JwtConfig(issuer = "http://security.invistus.com.br")

        val exception =
            assertThrows<IllegalArgumentException> {
                config.validateIssuer(activeEnvironmentNames = setOf("test"))
            }

        assertThat(exception.message).contains("JWT issuer must be an absolute HTTPS URL")
    }
}
