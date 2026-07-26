package io.github.salomax.neotool.banking.domain.transaction

import io.github.salomax.neotool.banking.service.transaction.BulkWriteCapDecision
import io.github.salomax.neotool.banking.service.transaction.BulkWriteCapGuard
import io.github.salomax.neotool.banking.service.transaction.McpWriteBulkConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("BulkWriteCapGuard")
class BulkWriteCapGuardTest {
    private val guard = BulkWriteCapGuard(McpWriteBulkConfiguration(maxAffectedRecords = 50))

    @Test
    fun `zero affected rows proceeds`() {
        assertThat(guard.evaluate(0)).isEqualTo(BulkWriteCapDecision.Proceed)
    }

    @Test
    fun `exactly at cap proceeds`() {
        assertThat(guard.evaluate(50)).isEqualTo(BulkWriteCapDecision.Proceed)
    }

    @Test
    fun `over cap requires confirmation`() {
        assertThat(guard.evaluate(51)).isEqualTo(BulkWriteCapDecision.ConfirmationRequired(51))
    }
}
