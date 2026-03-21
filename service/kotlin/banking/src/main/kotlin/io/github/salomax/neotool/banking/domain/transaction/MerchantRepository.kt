package io.github.salomax.neotool.banking.domain.transaction

import java.util.UUID

/**
 * Domain repository for [Merchant] and the logo cache.
 */
interface MerchantRepository {

    /** Returns system merchants + user-scoped merchants for [accountId], ordered for suggestion. */
    fun findAllForAccount(accountId: UUID): List<Merchant>

    /** Returns all user-scoped merchants for [accountId]. */
    fun findUserMerchantsForAccount(accountId: UUID): List<Merchant>

    /** Finds a merchant accessible to [accountId] (system or owned). */
    fun findById(id: UUID): Merchant?

    /** Persists a new or updated merchant. */
    fun save(merchant: Merchant): Merchant

    /** Returns the asset_id for a cached domain logo, or null if not yet cached. */
    fun findLogoAssetIdByDomain(domain: String): String?

    /**
     * Returns top-merchant rows for the given account, optionally filtered by
     * financial account, year and month. Results are ordered by transaction_count DESC.
     */
    fun findTopMerchants(
        accountId: UUID,
        financialAccountId: UUID?,
        year: Int?,
        month: Int?,
        limit: Int,
    ): List<TopMerchantRow>
}
