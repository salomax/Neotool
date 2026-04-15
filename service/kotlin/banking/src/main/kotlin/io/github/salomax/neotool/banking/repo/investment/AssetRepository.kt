package io.github.salomax.neotool.banking.repo.investment

import io.github.salomax.neotool.banking.domain.investment.Asset
import java.util.UUID

/**
 * Domain repository for [Asset].
 * Assets are global (no account_id scoping) — shared across all tenants.
 */
interface AssetRepository {
    fun findById(id: UUID): Asset?

    fun findByIds(ids: Collection<UUID>): List<Asset>

    fun findAllWithTicker(): List<Asset>

    fun findAllWithFundCnpj(): List<Asset>

    fun findByTicker(ticker: String): Asset?

    fun findByIsinCode(isinCode: String): Asset?

    fun findByFundCnpj(fundCnpj: String): Asset?

    /** Upsert by ticker (equities/FIIs/ETFs). */
    fun save(asset: Asset): Asset

    fun update(asset: Asset): Asset
}
