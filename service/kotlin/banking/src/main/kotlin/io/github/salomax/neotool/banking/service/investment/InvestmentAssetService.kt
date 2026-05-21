package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.domain.investment.Asset
import io.github.salomax.neotool.banking.domain.investment.AssetType
import io.github.salomax.neotool.banking.repo.investment.AssetRepository
import io.github.salomax.neotool.banking.repo.investment.HoldingRepository
import jakarta.inject.Singleton
import java.util.UUID

/**
 * Application service for the global asset catalog.
 * Assets are not tenant-scoped — they are shared across all accounts.
 */
@Singleton
class InvestmentAssetService(
    private val assetRepository: AssetRepository,
    private val holdingRepository: HoldingRepository,
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

    fun update(
        id: UUID,
        accountId: UUID,
        name: String,
        assetType: AssetType,
        issuerName: String?,
        fundCnpj: String?,
        ticker: String?,
        isinCode: String?,
        currencyCode: String,
    ): Asset {
        require(name.isNotBlank()) { "name is required" }
        require(holdingRepository.findByAccount(accountId).any { it.assetId == id }) {
            "Asset $id cannot be updated because it is not linked to holdings owned by account $accountId"
        }
        val existing = assetRepository.findById(id) ?: throw NoSuchElementException("Asset $id not found")
        return assetRepository.update(
            existing.copy(
                name = name,
                assetType = assetType,
                issuerName = issuerName?.trim()?.takeIf { it.isNotBlank() },
                fundCnpj = fundCnpj?.trim()?.takeIf { it.isNotBlank() },
                ticker = ticker?.trim()?.uppercase()?.takeIf { it.isNotBlank() },
                isinCode = isinCode?.trim()?.uppercase()?.takeIf { it.isNotBlank() },
                currencyCode = currencyCode,
            ),
        )
    }
}
