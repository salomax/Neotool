package io.github.salomax.neotool.assistant.test.unit.tool

import io.github.salomax.neotool.assistant.tool.JsonObject
import io.github.salomax.neotool.assistant.tool.ToolNotFoundException
import io.github.salomax.neotool.assistant.tool.ToolRegistry
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class ToolRegistryTest {
    @Test
    fun `execute forwards accountId and input to registered executor`() {
        val registry = ToolRegistry()
        var capturedAccountId: UUID? = null
        var capturedInput: JsonObject? = null

        registry.register("get_balance") { accountId, input ->
            capturedAccountId = accountId
            capturedInput = input
            Result.success(mapOf("balance" to 1000, "currency" to "BRL"))
        }

        val accountId = UUID.randomUUID()
        val input = mapOf("includePending" to true)

        val result = runBlocking { registry.execute("get_balance", accountId, input) }

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isEqualTo(mapOf("balance" to 1000, "currency" to "BRL"))
        assertThat(capturedAccountId).isEqualTo(accountId)
        assertThat(capturedInput).isEqualTo(input)
    }

    @Test
    fun `execute returns ToolNotFoundException for unknown tool`() {
        val registry = ToolRegistry()
        val result = runBlocking { registry.execute("unknown_tool", UUID.randomUUID(), emptyMap()) }

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(ToolNotFoundException::class.java)
        assertThat(result.exceptionOrNull()!!.message).contains("unknown_tool")
    }
}
