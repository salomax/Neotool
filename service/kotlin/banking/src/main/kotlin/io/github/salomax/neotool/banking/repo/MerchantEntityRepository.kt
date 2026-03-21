package io.github.salomax.neotool.banking.repo

import io.github.salomax.neotool.banking.model.transaction.MerchantEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.util.UUID

@Repository
interface MerchantEntityRepository : JpaRepository<MerchantEntity, UUID> {

    /**
     * System merchants (global) + user-scoped merchants for [accountId].
     * Sorted: user-scoped first (account_id IS NOT NULL), then by name length DESC.
     */
    @Query(
        value = """
            SELECT m FROM MerchantEntity m
            WHERE m.accountId IS NULL OR m.accountId = :accountId
            ORDER BY
                CASE WHEN m.accountId IS NOT NULL THEN 0 ELSE 1 END ASC,
                LENGTH(m.name) DESC,
                m.name ASC
        """,
    )
    fun findAllForAccount(accountId: UUID): List<MerchantEntity>

    /** User-scoped merchants only. */
    @Query(
        value = """
            SELECT m FROM MerchantEntity m
            WHERE m.accountId = :accountId
            ORDER BY m.name ASC
        """,
    )
    fun findUserMerchantsForAccount(accountId: UUID): List<MerchantEntity>

    fun findByIdIn(ids: Collection<UUID>): List<MerchantEntity>

    /**
     * Top merchants for an account, filtered by optional financial account and date range.
     * Delegates to the v_top_merchants view.
     */
    @Query(
        nativeQuery = true,
        value = """
            SELECT
                merchant_id,
                merchant_name,
                merchant_domain,
                merchant_sector,
                transaction_count,
                total_amount,
                last_used_at
            FROM banking.v_top_merchants
            WHERE account_id = :accountId
              AND (:financialAccountId IS NULL OR financial_account_id = :financialAccountId::uuid)
              AND (:year IS NULL OR EXTRACT(YEAR FROM last_used_at) = :year)
              AND (:month IS NULL OR EXTRACT(MONTH FROM last_used_at) = :month)
            ORDER BY transaction_count DESC
            LIMIT :limit
        """,
    )
    fun findTopMerchants(
        accountId: UUID,
        financialAccountId: UUID?,
        year: Int?,
        month: Int?,
        limit: Int,
    ): List<Array<Any?>>
}
