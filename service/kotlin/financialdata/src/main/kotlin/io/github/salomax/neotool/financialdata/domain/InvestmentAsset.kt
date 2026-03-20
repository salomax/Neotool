package io.github.salomax.neotool.financialdata.domain

import java.util.UUID

/**
 * Domain object for a B3-listed investment asset (equity, FII, option, etc.).
 */
data class ListedAsset(
    val id: UUID,
    val ticker: String,
    val name: String,
    val assetType: String,
    val exchange: String?,
    val isActive: Boolean,
    // Latest price — fetched inline via LATERAL join on list/detail queries.
    val latestPrice: AssetDailyPrice? = null,
    // Trigram similarity score used internally for cursor pagination when a search query is active.
    val simScore: Double = 0.0,
)
