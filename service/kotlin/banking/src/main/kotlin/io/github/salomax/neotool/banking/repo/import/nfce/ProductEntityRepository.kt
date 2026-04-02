package io.github.salomax.neotool.banking.repo.import.nfce

import io.github.salomax.neotool.banking.model.import.nfce.ProductEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.util.UUID

@Repository
interface ProductEntityRepository : JpaRepository<ProductEntity, UUID> {
    fun findByIdAndAccountId(
        id: UUID,
        accountId: UUID,
    ): ProductEntity?

    fun findByIdInAndAccountId(
        ids: Collection<UUID>,
        accountId: UUID,
    ): List<ProductEntity>

    @Query(
        """
        SELECT DISTINCT p.*
        FROM banking.products p
        LEFT JOIN banking.product_variants pv ON pv.product_id = p.id
        WHERE p.account_id = :accountId
          AND (:category IS NULL OR p.category = :category)
          AND (
            :search IS NULL OR :search = '' OR
            p.name ILIKE CONCAT('%', :search, '%') OR
            pv.name ILIKE CONCAT('%', :search, '%') OR
            COALESCE(pv.brand, '') ILIKE CONCAT('%', :search, '%')
          )
        ORDER BY p.name ASC
        """,
        nativeQuery = true,
    )
    fun listForAccount(
        accountId: UUID,
        search: String?,
        category: String?,
    ): List<ProductEntity>

    @Query(
        """
        SELECT p.*
        FROM banking.products p
        WHERE p.account_id = :accountId
          AND LOWER(p.name) = LOWER(:name)
        LIMIT 1
        """,
        nativeQuery = true,
    )
    fun findByAccountIdAndExactName(
        accountId: UUID,
        name: String,
    ): ProductEntity?
}
