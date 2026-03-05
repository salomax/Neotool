package io.github.salomax.neotool.assistant.test.unit.tool

import io.github.salomax.neotool.assistant.tool.builtin.GetCurrentTimeExecutor
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class GetCurrentTimeExecutorTest {
    @Test
    fun `execute returns UTC ISO-8601 datetime payload`() {
        val executor = GetCurrentTimeExecutor()

        val result = runBlocking { executor.execute(UUID.randomUUID(), emptyMap()) }

        assertThat(result.isSuccess).isTrue()
        val payload = result.getOrThrow()
        assertThat(payload["timezone"]).isEqualTo("UTC")

        val datetime = payload["datetime"] as String
        assertThat(datetime).endsWith("Z")
        assertThat(Instant.parse(datetime)).isNotNull
    }
}
