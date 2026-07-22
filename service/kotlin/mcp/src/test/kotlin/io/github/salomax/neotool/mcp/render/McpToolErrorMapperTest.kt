package io.github.salomax.neotool.mcp.render

import io.github.salomax.neotool.mcp.protocol.McpToolExecutionException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class McpToolErrorMapperTest {
    private val mapper = McpToolErrorMapper()

    @Test
    fun `filters tool error parameters through allowlist`() {
        val result =
            mapper.toResult(
                McpToolExecutionException(
                    code = "BANKING_AUTHORIZATION_DENIED",
                    service = "banking",
                    parameters =
                        mapOf(
                            "status" to 403,
                            "limit" to 50,
                            "secretAccountId" to "account-id",
                            "query" to "internal query text",
                        ),
                ),
            )

        assertThat(result.parameters).containsEntry("status", 403)
        assertThat(result.parameters).containsEntry("limit", 50)
        assertThat(result.parameters).doesNotContainKeys("secretAccountId", "query")
    }
}
