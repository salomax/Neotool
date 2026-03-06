package io.github.salomax.neotool.banking.domain.investment

import java.math.BigDecimal
import java.time.Instant

/**
 * An investable instrument in the asset catalog.
 *
 * Scope: global (not user-specific). Equities are identified by [ticker];
 * fixed income instruments by [isinCode] or created on first encounter;
 * funds by [fundCnpj].
 *
 * Market prices are NOT stored here — they are fetched from the financial-data service.
 */
data class Asset(
    val id: java.util.UUID? = null,

    // Identity
    val ticker: String? = null,           // B3 ticker (PETR4, MXRF11). Null for RF/funds.
    val isinCode: String? = null,         // ISO 6166, 12 chars. Null when not available.
    val name: String,

    // Classification
    val assetType: AssetType,
    val exchange: String? = null,         // B3, NYSE, etc. Null for non-exchange assets.
    val currencyCode: String = "BRL",

    // Issuer (RF and funds)
    val issuerCnpj: String? = null,
    val issuerName: String? = null,

    // Fund-specific
    val fundCnpj: String? = null,
    val anbimaCategory: AnbimaCategory? = null,
    val anbimaClass: String? = null,
    val anbimaSubclass: String? = null,

    // Equity-specific
    // priceFactor: B3 pricing multiplier. Most assets = 1.
    // Some debentures quoted per 1000 units use 0.001.
    val priceFactor: BigDecimal = BigDecimal.ONE,

    // External reference
    val providerId: String? = null,

    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
)
