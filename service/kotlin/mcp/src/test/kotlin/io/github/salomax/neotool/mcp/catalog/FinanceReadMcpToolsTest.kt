package io.github.salomax.neotool.mcp.catalog

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FinanceReadMcpToolsTest {
    @Test
    fun `read catalog definitions are complete and MCP-visible`() {
        assertThat(FinanceReadMcpTools.all).hasSize(13)
        assertThat(FinanceReadMcpTools.all.map { it.name }).doesNotHaveDuplicates()
        assertThat(FinanceReadMcpTools.all.map { it.name }).containsExactly(
            "list_transactions",
            "get_transaction",
            "get_month_summary",
            "get_period_summary",
            "get_cash_flow_series",
            "get_spending_by_category",
            "get_spending_by_tag",
            "get_top_merchants",
            "list_financial_accounts",
            "list_credit_card_bills",
            "get_credit_card_bill",
            "list_categories",
            "list_transaction_rules",
        )
        assertThat(FinanceReadMcpTools.all.map { it.requiredScope })
            .allMatch {
                it in
                    setOf(
                        "finance:transactions:read",
                        "finance:cashflow:read",
                        "finance:accounts:read",
                        "finance:bills:read",
                        "finance:categories:read",
                        "finance:rules:read",
                    )
            }
        assertThat(FinanceReadMcpTools.all.map { it.description }).allMatch { it.isNotBlank() }
    }
}
