package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.domain.investment.*
import io.github.salomax.neotool.banking.repo.investment.AssetRepository
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
        fundCnpj: String?,
        ticker: String?,
        isinCode: String?,
        currencyCode: String,
    ): Asset {
        require(name.isNotBlank()) { "name is required" }
        val normalizedFundCnpj = fundCnpj?.trim()?.takeIf { it.isNotBlank() }
        val normalizedTicker = ticker?.trim()?.uppercase()?.takeIf { it.isNotBlank() }
        val normalizedIsinCode = isinCode?.trim()?.uppercase()?.takeIf { it.isNotBlank() }

        if (normalizedFundCnpj != null) {
            assetRepository.findByFundCnpj(normalizedFundCnpj)?.let { return it }
        }
        if (normalizedTicker != null) {
            assetRepository.findByTicker(normalizedTicker)?.let { return it }
        }
        if (normalizedIsinCode != null) {
            assetRepository.findByIsinCode(normalizedIsinCode)?.let { return it }
        }

        return assetRepository.save(
            Asset(
                name = name,
                assetType = assetType,
                issuerName = issuerName,
                fundCnpj = normalizedFundCnpj,
                ticker = normalizedTicker,
                isinCode = normalizedIsinCode,
                currencyCode = currencyCode,
            ),
        )
    }
}
