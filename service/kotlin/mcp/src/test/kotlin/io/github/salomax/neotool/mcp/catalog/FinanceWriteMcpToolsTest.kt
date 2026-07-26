package io.github.salomax.neotool.mcp.catalog

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FinanceWriteMcpToolsTest {
    @Test
    fun `write catalog definitions are complete and scope-gated`() {
        assertThat(FinanceWriteMcpTools.all).hasSize(6)
        assertThat(FinanceWriteMcpTools.all.map { it.name }).containsExactly(
            "create_transaction",
            "update_transaction",
            "delete_transaction",
            "create_transaction_rule",
            "categorize_transactions",
            "apply_rule_to_historical",
        )
        assertThat(FinanceWriteMcpTools.all.map { it.requiredScope }).containsExactly(
            "finance:transactions:write",
            "finance:transactions:write",
            "finance:transactions:write",
            "finance:rules:write",
            "finance:transactions:write",
            "finance:rules:write",
        )
        assertThat(FinanceWriteMcpTools.all.map { it.description }).allMatch { it.isNotBlank() }
    }
}
