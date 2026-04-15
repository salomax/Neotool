package io.github.salomax.neotool.banking.repo.transaction

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

    @Query(
        nativeQuery = true,
        value = """
            SELECT m.*
            FROM banking.merchants m
            WHERE (m.account_id IS NULL OR m.account_id = :accountId)
              AND (
                    CAST(:searchLikePattern AS text) IS NULL
                    OR m.display_name ILIKE CAST(:searchLikePattern AS text) ESCAPE E'\\'
                  )
              AND (
                    CAST(:afterName AS text) IS NULL
                    OR m.display_name > CAST(:afterName AS text)
                    OR (
                        m.display_name = CAST(:afterName AS text)
                        AND m.id > CAST(:afterId AS uuid)
                    )
              )
            ORDER BY m.display_name ASC, m.id ASC
            LIMIT :limit
        """,
    )
    fun searchPageForAccount(
        accountId: UUID,
        searchLikePattern: String?,
        afterName: String?,
        afterId: UUID?,
        limit: Int,
    ): List<MerchantEntity>

    @Query(
        nativeQuery = true,
        value = """
            SELECT COUNT(*)
            FROM banking.merchants m
            WHERE (m.account_id IS NULL OR m.account_id = :accountId)
              AND (
                    CAST(:searchLikePattern AS text) IS NULL
                    OR m.display_name ILIKE CAST(:searchLikePattern AS text) ESCAPE E'\\'
                  )
        """,
    )
    fun countForAccountBySearchLike(
        accountId: UUID,
        searchLikePattern: String?,
    ): Long

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

    @Query(
        nativeQuery = true,
        value = """
            SELECT DISTINCT m.domain
            FROM banking.merchants m
            LEFT JOIN banking.merchant_logo_cache mlc ON mlc.domain = m.domain
            WHERE m.domain IS NOT NULL
              AND m.domain <> ''
              AND mlc.domain IS NULL
            ORDER BY m.domain ASC
            LIMIT :limit
        """,
    )
    fun findDomainsMissingLogo(limit: Int): List<String>
}
