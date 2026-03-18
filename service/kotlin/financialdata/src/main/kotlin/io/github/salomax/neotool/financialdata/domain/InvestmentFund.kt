package io.github.salomax.neotool.financialdata.domain

import java.util.UUID

/**
 * Domain object for an investment fund from the CVM catalog.
 */
data class InvestmentFund(
    val id: UUID,
    val name: String,
    val cnpj: String,
    val assetType: String,
    val isActive: Boolean,
    // Latest NAV — present when fetched inline (list/detail). Null if not yet available.
    val latestNav: FundDailyNav? = null,
    // Trigram similarity score used internally for cursor-based pagination when a search
    // query is active. Not exposed in the GraphQL schema.
    val simScore: Double = 0.0,
)
