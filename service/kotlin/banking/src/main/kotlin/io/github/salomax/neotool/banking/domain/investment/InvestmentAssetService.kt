package io.github.salomax.neotool.banking.domain.investment

import jakarta.inject.Singleton
import java.util.UUID

/**
 * Application service for the global asset catalog.
 * Assets are not tenant-scoped — they are shared across all accounts.
 */
@Singleton
class InvestmentAssetService(
    private val assetRepository: AssetRepository,
) {
    fun findById(id: UUID): Asset? = assetRepository.findById(id)

    fun create(
        name: String,
        assetType: AssetType,
        issuerName: String?,
        ticker: String?,
        isinCode: String?,
        currencyCode: String,
    ): Asset {
        require(name.isNotBlank()) { "name is required" }
        return assetRepository.save(
            Asset(
                name = name,
                assetType = assetType,
                issuerName = issuerName,
                ticker = ticker,
                isinCode = isinCode,
                currencyCode = currencyCode,
            ),
        )
    }
}
