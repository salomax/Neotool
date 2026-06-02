package io.github.salomax.neotool.banking.repo.import.nfce

import io.github.salomax.neotool.banking.model.import.nfce.PurchaseItemEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

interface UnmappedItemGroupRow {
    fun getRawDescription(): String

    fun getItemCount(): Long
}

interface ProductSummaryRow {
    fun getProductId(): UUID

    fun getAveragePrice(): BigDecimal?

    fun getTotalSpent(): BigDecimal?

    fun getPurchaseCount(): Long
}

interface VariantAveragePriceRow {
    fun getVariantId(): UUID

    fun getAverageUnitPrice(): BigDecimal?
}

interface PricePointRow {
    fun getDate(): LocalDate

    fun getUnitPrice(): BigDecimal

    fun getStoreName(): String?

    fun getMunicipality(): String?

    fun getState(): String?
}

interface ConsumptionTrendRow {
    fun getMonth(): LocalDate

    fun getTotalQuantity(): BigDecimal?

    fun getTotalSpent(): BigDecimal?

    fun getPurchaseCount(): Long
}

interface StorePriceComparisonRow {
    fun getStoreName(): String?

    fun getMunicipality(): String?

    fun getState(): String?

    fun getAverageUnitPrice(): BigDecimal?

    fun getPurchaseCount(): Long

    fun getLastPurchaseDate(): LocalDate?
}

interface NcmSpendingRow {
    fun getNcmCode(): String

    fun getNcmDescription(): String?

    fun getMonth(): LocalDate

    fun getTotalSpent(): BigDecimal?

    fun getItemCount(): Long
}

@Repository
interface PurchaseItemEntityRepository : JpaRepository<PurchaseItemEntity, UUID> {
    fun findByIdAndAccountId(
        id: UUID,
        accountId: UUID,
    ): PurchaseItemEntity?

    fun findByTransactionIdAndAccountIdOrderBySequenceAsc(
        transactionId: UUID,
        accountId: UUID,
    ): List<PurchaseItemEntity>

    fun findTop3ByAccountIdAndRawDescriptionAndProductVariantIdIsNullOrderByCreatedAtDesc(
        accountId: UUID,
        rawDescription: String,
    ): List<PurchaseItemEntity>

    @Query(
        """
        SELECT pi.* FROM banking.purchase_items pi
        JOIN banking.purchase_documents nd ON nd.transaction_id = pi.transaction_id
        WHERE pi.account_id = :accountId
          AND nd.issuer_cnpj = :issuerCnpj
          AND pi.issuer_product_code = :issuerProductCode
          AND pi.product_variant_id IS NOT NULL
        ORDER BY pi.created_at DESC
        LIMIT 1
        """,
        nativeQuery = true,
    )
    fun findFirstResolvedByCnpjAndProduct(
        accountId: UUID,
        issuerCnpj: String,
        issuerProductCode: String,
    ): PurchaseItemEntity?

    @Query(
        """
        SELECT pi.raw_description AS rawDescription, COUNT(*) AS itemCount
        FROM banking.purchase_items pi
        WHERE pi.account_id = :accountId
          AND pi.product_variant_id IS NULL
        GROUP BY pi.raw_description
        ORDER BY COUNT(*) DESC, MAX(pi.created_at) DESC
        LIMIT :limit
        """,
        nativeQuery = true,
    )
    fun findUnmappedGroups(
        accountId: UUID,
        limit: Int,
    ): List<UnmappedItemGroupRow>

    @Query(
        """
        UPDATE banking.purchase_items
        SET product_variant_id = :productVariantId
        WHERE account_id = :accountId
          AND raw_description = :rawDescription
          AND product_variant_id IS NULL
        """,
        nativeQuery = true,
    )
    fun mapUnmappedItemsToVariant(
        accountId: UUID,
        rawDescription: String,
        productVariantId: UUID,
    ): Long

    @Query(
        """
        UPDATE banking.purchase_items
        SET product_variant_id = NULL
        WHERE account_id = :accountId
          AND raw_description = :rawDescription
          AND product_variant_id = :productVariantId
        """,
        nativeQuery = true,
    )
    fun clearMappedItemsForAlias(
        accountId: UUID,
        rawDescription: String,
        productVariantId: UUID,
    ): Long

    @Query(
        """
        SELECT
            pv.product_id AS productId,
            AVG(
                CASE
                    WHEN pv.normalized_quantity_value IS NOT NULL AND pv.normalized_quantity_value > 0
                        THEN pi.effective_price / pv.normalized_quantity_value
                    ELSE NULL
                END
            ) AS averagePrice,
            COALESCE(SUM(pi.effective_price), 0) AS totalSpent,
            COUNT(*) AS purchaseCount
        FROM banking.purchase_items pi
        JOIN banking.product_variants pv ON pv.id = pi.product_variant_id
        JOIN banking.transactions t ON t.id = pi.transaction_id
        WHERE pi.account_id = :accountId
          AND pv.product_id IN (:productIds)
          AND t.deleted_at IS NULL
        GROUP BY pv.product_id
        """,
        nativeQuery = true,
    )
    fun summarizeProducts(
        accountId: UUID,
        productIds: Collection<UUID>,
    ): List<ProductSummaryRow>

    @Query(
        """
        SELECT
            pv.id AS variantId,
            AVG(
                CASE
                    WHEN pv.normalized_quantity_value IS NOT NULL AND pv.normalized_quantity_value > 0
                        THEN pi.effective_price / pv.normalized_quantity_value
                    ELSE NULL
                END
            ) AS averageUnitPrice
        FROM banking.purchase_items pi
        JOIN banking.product_variants pv ON pv.id = pi.product_variant_id
        JOIN banking.transactions t ON t.id = pi.transaction_id
        WHERE pi.account_id = :accountId
          AND pv.id IN (:variantIds)
          AND t.deleted_at IS NULL
        GROUP BY pv.id
        """,
        nativeQuery = true,
    )
    fun summarizeVariants(
        accountId: UUID,
        variantIds: Collection<UUID>,
    ): List<VariantAveragePriceRow>

    @Query(
        """
        SELECT
            t.date AS date,
            (pi.effective_price / pv.normalized_quantity_value) AS unitPrice,
            nd.issuer_name AS storeName,
            nd.municipality AS municipality,
            nd.state AS state
        FROM banking.purchase_items pi
        JOIN banking.product_variants pv ON pv.id = pi.product_variant_id
        JOIN banking.transactions t ON t.id = pi.transaction_id
        LEFT JOIN banking.purchase_documents nd ON nd.transaction_id = t.id
        WHERE pi.account_id = :accountId
          AND pv.product_id = :productId
          AND (:variantId IS NULL OR pv.id = :variantId)
          AND t.deleted_at IS NULL
          AND pv.normalized_quantity_value IS NOT NULL
          AND pv.normalized_quantity_value > 0
          AND (:startDate IS NULL OR t.date >= :startDate)
          AND (:endDate IS NULL OR t.date <= :endDate)
        ORDER BY t.date ASC, pi.created_at ASC
        LIMIT 500
        """,
        nativeQuery = true,
    )
    fun findPriceHistory(
        accountId: UUID,
        productId: UUID,
        variantId: UUID?,
        startDate: LocalDate?,
        endDate: LocalDate?,
    ): List<PricePointRow>

    @Query(
        """
        SELECT
            DATE_TRUNC('month', t.date)::date AS month,
            COALESCE(
                SUM(
                    CASE
                        WHEN pv.normalized_quantity_value IS NOT NULL AND pv.normalized_quantity_value > 0
                            THEN pi.quantity * pv.normalized_quantity_value
                        ELSE pi.quantity
                    END
                ),
                0
            ) AS totalQuantity,
            COALESCE(SUM(pi.effective_price), 0) AS totalSpent,
            COUNT(DISTINCT t.id) AS purchaseCount
        FROM banking.purchase_items pi
        JOIN banking.product_variants pv ON pv.id = pi.product_variant_id
        JOIN banking.transactions t ON t.id = pi.transaction_id
        WHERE pi.account_id = :accountId
          AND pv.product_id = :productId
          AND t.deleted_at IS NULL
          AND t.date >= :startDate
        GROUP BY DATE_TRUNC('month', t.date)::date
        ORDER BY month ASC
        """,
        nativeQuery = true,
    )
    fun findConsumptionTrend(
        accountId: UUID,
        productId: UUID,
        startDate: LocalDate,
    ): List<ConsumptionTrendRow>

    @Query(
        """
        SELECT
            nd.issuer_name AS storeName,
            nd.municipality AS municipality,
            nd.state AS state,
            AVG(
                CASE
                    WHEN pv.normalized_quantity_value IS NOT NULL AND pv.normalized_quantity_value > 0
                        THEN pi.effective_price / pv.normalized_quantity_value
                    ELSE NULL
                END
            ) AS averageUnitPrice,
            COUNT(*) AS purchaseCount,
            MAX(t.date) AS lastPurchaseDate
        FROM banking.purchase_items pi
        JOIN banking.product_variants pv ON pv.id = pi.product_variant_id
        JOIN banking.transactions t ON t.id = pi.transaction_id
        LEFT JOIN banking.purchase_documents nd ON nd.transaction_id = t.id
        WHERE pi.account_id = :accountId
          AND pv.product_id = :productId
          AND t.deleted_at IS NULL
          AND pv.normalized_quantity_value IS NOT NULL
          AND pv.normalized_quantity_value > 0
          AND (:startDate IS NULL OR t.date >= :startDate)
          AND (:endDate IS NULL OR t.date <= :endDate)
        GROUP BY nd.issuer_name, nd.municipality, nd.state
        ORDER BY averageUnitPrice ASC NULLS LAST, purchaseCount DESC, nd.issuer_name ASC NULLS LAST
        """,
        nativeQuery = true,
    )
    fun findStorePriceComparisons(
        accountId: UUID,
        productId: UUID,
        startDate: LocalDate?,
        endDate: LocalDate?,
    ): List<StorePriceComparisonRow>

    @Query(
        """
        SELECT
            LEFT(pi.ncm_code, :depth) AS ncmCode,
            MAX(nc.description) AS ncmDescription,
            DATE_TRUNC('month', t.date)::date AS month,
            COALESCE(SUM(pi.effective_price), 0) AS totalSpent,
            COUNT(*) AS itemCount
        FROM banking.purchase_items pi
        JOIN banking.transactions t ON t.id = pi.transaction_id
        LEFT JOIN banking.ncm_codes nc ON nc.code = LEFT(pi.ncm_code, :depth)
        WHERE pi.account_id = :accountId
          AND t.deleted_at IS NULL
          AND pi.ncm_code IS NOT NULL
          AND pi.ncm_code <> '00000000'
          AND (:startDate IS NULL OR t.date >= :startDate)
          AND (:endDate IS NULL OR t.date <= :endDate)
        GROUP BY LEFT(pi.ncm_code, :depth), DATE_TRUNC('month', t.date)::date
        ORDER BY totalSpent DESC, month ASC
        """,
        nativeQuery = true,
    )
    fun findSpendingByNcm(
        accountId: UUID,
        depth: Int,
        startDate: LocalDate?,
        endDate: LocalDate?,
    ): List<NcmSpendingRow>
}
