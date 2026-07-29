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
    val manager: String? = null,
    val administrador: String? = null,
    val tributacaoLongoPrazo: String? = null,
    val publicoAlvo: String? = null,
    val patrimonioLiquido: String? = null,
    val dataPatrimonioLiquido: String? = null,
    val cnpjAuditor: String? = null,
    val auditor: String? = null,
    val cnpjCustodiante: String? = null,
    val custodiante: String? = null,
    // Latest NAV — present when fetched inline (list/detail). Null if not yet available.
    val latestNav: FundDailyNav? = null,
    // Trigram similarity score used internally for cursor-based pagination when a search
    // query is active. Not exposed in the GraphQL schema.
    val simScore: Double = 0.0,
)
