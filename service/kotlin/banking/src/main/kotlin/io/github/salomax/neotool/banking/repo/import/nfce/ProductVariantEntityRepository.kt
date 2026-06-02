package io.github.salomax.neotool.banking.repo.import.nfce

import io.github.salomax.neotool.banking.model.import.nfce.ProductVariantEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.util.UUID

@Repository
interface ProductVariantEntityRepository : JpaRepository<ProductVariantEntity, UUID> {
    fun findByEan(ean: String): ProductVariantEntity?

    fun findByIdAndAccountId(
        id: UUID,
        accountId: UUID,
    ): ProductVariantEntity?

    fun findByIdInAndAccountId(
        ids: Collection<UUID>,
        accountId: UUID,
    ): List<ProductVariantEntity>

    fun findByProductIdInAndAccountIdOrderByNameAsc(
        productIds: Collection<UUID>,
        accountId: UUID,
    ): List<ProductVariantEntity>

    @Query(
        """
        SELECT pv.*
        FROM banking.product_variants pv
        JOIN banking.products p ON p.id = pv.product_id
        WHERE pv.account_id = :accountId
          AND (
            :search IS NULL OR :search = '' OR
            pv.name ILIKE CONCAT('%', :search, '%') OR
            COALESCE(pv.brand, '') ILIKE CONCAT('%', :search, '%') OR
            p.name ILIKE CONCAT('%', :search, '%') OR
            pv.ean = :search
          )
        ORDER BY pv.updated_at DESC, pv.name ASC
        LIMIT :limit
        """,
        nativeQuery = true,
    )
    fun searchForAccount(
        accountId: UUID,
        search: String?,
        limit: Int,
    ): List<ProductVariantEntity>
}
