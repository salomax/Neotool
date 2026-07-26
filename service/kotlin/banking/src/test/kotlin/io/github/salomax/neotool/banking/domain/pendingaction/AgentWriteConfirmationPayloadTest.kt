package io.github.salomax.neotool.banking.domain.pendingaction

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class AgentWriteConfirmationPayloadTest {
    @Test
    fun `delete payload round-trips from map`() {
        val origin = AgentWriteOrigin(clientId = "mcp-client", toolCallId = UUID.randomUUID(), tokenId = "token-1")
        val payload = DeleteTransactionConfirmationPayload(transactionId = UUID.randomUUID(), origin = origin)

        val parsed = AgentWriteConfirmationPayloads.fromMap(payload.toPayloadMap())

        assertThat(parsed).isInstanceOf(DeleteTransactionConfirmationPayload::class.java)
        parsed as DeleteTransactionConfirmationPayload
        assertThat(parsed.transactionId).isEqualTo(payload.transactionId)
        assertThat(parsed.origin).isEqualTo(origin)
    }

    @Test
    fun `bulk payload round-trips from map`() {
        val origin = AgentWriteOrigin(clientId = "mcp-client", toolCallId = UUID.randomUUID(), tokenId = "token-1")
        val payload =
            BulkCategorizeConfirmationPayload(
                transactionIds = listOf(UUID.randomUUID(), UUID.randomUUID()),
                categoryId = UUID.randomUUID(),
                origin = origin,
            )

        val parsed = AgentWriteConfirmationPayloads.fromMap(payload.toPayloadMap())

        assertThat(parsed).isInstanceOf(BulkCategorizeConfirmationPayload::class.java)
        parsed as BulkCategorizeConfirmationPayload
        assertThat(parsed.transactionIds).containsExactlyElementsOf(payload.transactionIds)
        assertThat(parsed.categoryId).isEqualTo(payload.categoryId)
        assertThat(parsed.origin).isEqualTo(origin)
    }

    @Test
    fun `rule payload round-trips from map`() {
        val origin = AgentWriteOrigin(clientId = "mcp-client", toolCallId = UUID.randomUUID(), tokenId = "token-1")
        val payload = ApplyRuleToHistoricalConfirmationPayload(ruleId = UUID.randomUUID(), origin = origin)

        val parsed = AgentWriteConfirmationPayloads.fromMap(payload.toPayloadMap())

        assertThat(parsed).isInstanceOf(ApplyRuleToHistoricalConfirmationPayload::class.java)
        parsed as ApplyRuleToHistoricalConfirmationPayload
        assertThat(parsed.ruleId).isEqualTo(payload.ruleId)
        assertThat(parsed.origin).isEqualTo(origin)
    }
}
