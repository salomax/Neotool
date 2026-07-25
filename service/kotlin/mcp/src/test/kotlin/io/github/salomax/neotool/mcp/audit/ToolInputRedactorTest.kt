package io.github.salomax.neotool.mcp.audit

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ToolInputRedactorTest {
    private val redactor = ToolInputRedactor()

    @Test
    fun `redaction masks top level and nested sensitive fields`() {
        val arguments =
            mapOf(
                "query" to "transactions",
                "accessToken" to "secret-token",
                "credentials" to
                    mapOf(
                        "secret" to "top-secret",
                        "region" to "br",
                    ),
                "items" to
                    listOf(
                        mapOf("secret" to "nested-secret", "label" to "first"),
                    ),
            )

        val redacted = redactor.redact(arguments, listOf("accessToken", "secret"))

        assertThat(redacted["query"]).isEqualTo("transactions")
        assertThat(redacted["accessToken"]).isEqualTo(ToolInputRedactor.REDACTED_VALUE)
        assertThat((redacted["credentials"] as Map<*, *>)["secret"]).isEqualTo(ToolInputRedactor.REDACTED_VALUE)
        assertThat((redacted["credentials"] as Map<*, *>)["region"]).isEqualTo("br")
        val nestedItem = (redacted["items"] as List<*>).first() as Map<*, *>
        assertThat(nestedItem["secret"]).isEqualTo(ToolInputRedactor.REDACTED_VALUE)
        assertThat(nestedItem["label"]).isEqualTo("first")
    }

    @Test
    fun `redaction is a no op when tool declares no sensitive fields`() {
        val arguments = mapOf("query" to "transactions", "accessToken" to "secret-token")

        val redacted = redactor.redact(arguments, emptyList())

        assertThat(redacted).isEqualTo(arguments)
    }

    @Test
    fun `redact all masks every scalar while preserving shape`() {
        val arguments =
            mapOf(
                "query" to "transactions",
                "credentials" to mapOf("secret" to "top-secret", "region" to "br"),
                "items" to listOf(mapOf("secret" to "nested-secret", "label" to "first")),
            )

        val redacted = redactor.redactAll(arguments)

        assertThat(redacted["query"]).isEqualTo(ToolInputRedactor.REDACTED_VALUE)
        assertThat((redacted["credentials"] as Map<*, *>)["secret"]).isEqualTo(ToolInputRedactor.REDACTED_VALUE)
        assertThat((redacted["credentials"] as Map<*, *>)["region"]).isEqualTo(ToolInputRedactor.REDACTED_VALUE)
        val nestedItem = (redacted["items"] as List<*>).first() as Map<*, *>
        assertThat(nestedItem["secret"]).isEqualTo(ToolInputRedactor.REDACTED_VALUE)
        assertThat(nestedItem["label"]).isEqualTo(ToolInputRedactor.REDACTED_VALUE)
    }
}
