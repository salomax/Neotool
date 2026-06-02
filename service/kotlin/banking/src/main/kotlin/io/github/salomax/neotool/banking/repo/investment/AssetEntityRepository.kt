package io.github.salomax.neotool.banking.repo.investment

import io.github.salomax.neotool.banking.domain.investment.AssetType
import io.github.salomax.neotool.banking.model.investment.AssetEntity
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.util.UUID

/**
 * JPA repository for banking.investment_assets (entity layer).
 */
@Repository
interface AssetEntityRepository : JpaRepository<AssetEntity, UUID> {
    fun findByIdIn(ids: Collection<UUID>): List<AssetEntity>

    fun findByTickerIsNotNull(): List<AssetEntity>

    fun findByFundCnpjIsNotNull(): List<AssetEntity>

    fun findByTicker(ticker: String): AssetEntity?

    fun findByIsinCode(isinCode: String): AssetEntity?

    fun findByFundCnpj(fundCnpj: String): AssetEntity?

    fun findByProviderId(providerId: String): AssetEntity?

    fun findByNameAndAssetTypeAndIssuerName(
        name: String,
        assetType: AssetType,
        issuerName: String?,
    ): AssetEntity?
}
