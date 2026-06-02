package io.github.salomax.neotool.banking.service.import.nfce

import io.github.salomax.neotool.banking.repo.import.nfce.ProductRepository
import io.github.salomax.neotool.banking.repo.import.nfce.ProductVariantRepository
import io.github.salomax.neotool.banking.repo.import.nfce.PurchaseItemEntityRepository
import jakarta.inject.Singleton
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

data class ProductSummary(
    val productId: UUID,
    val averagePrice: BigDecimal?,
    val totalSpent: BigDecimal,
    val purchaseCount: Long,
)

data class VariantPriceSummary(
    val variantId: UUID,
    val averageUnitPrice: BigDecimal?,
)

data class PricePoint(
    val date: LocalDate,
    val unitPrice: BigDecimal,
    val storeName: String?,
    val municipality: String?,
    val state: String?,
)

data class ConsumptionTrendPoint(
    val month: LocalDate,
    val totalQuantity: BigDecimal,
    val totalSpent: BigDecimal,
    val purchaseCount: Long,
)

data class ConsumptionTrend(
    val productId: UUID,
    val periodMonths: Int,
    val totalQuantity: BigDecimal,
    val totalSpent: BigDecimal,
    val purchaseCount: Long,
    val months: List<ConsumptionTrendPoint>,
)

data class StorePriceComparison(
    val storeName: String?,
    val municipality: String?,
    val state: String?,
    val averageUnitPrice: BigDecimal?,
    val purchaseCount: Long,
    val lastPurchaseDate: LocalDate?,
)

data class NcmMonthlySpending(
    val month: LocalDate,
    val totalSpent: BigDecimal,
    val itemCount: Long,
)

data class NcmSpending(
    val ncmCode: String,
    val ncmDescription: String?,
    val totalSpent: BigDecimal,
    val itemCount: Long,
    val months: List<NcmMonthlySpending>,
)

@Singleton
open class ProductAnalyticsService(
    private val productRepository: ProductRepository,
    private val productVariantRepository: ProductVariantRepository,
    private val purchaseItemEntityRepository: PurchaseItemEntityRepository,
) {
    fun getProductSummaries(
        accountId: UUID,
        productIds: Collection<UUID>,
    ): Map<UUID, ProductSummary> {
        if (productIds.isEmpty()) return emptyMap()
        return purchaseItemEntityRepository.summarizeProducts(accountId, productIds)
            .associate { row ->
                row.getProductId() to
                    ProductSummary(
                        productId = row.getProductId(),
                        averagePrice = row.getAveragePrice(),
                        totalSpent = row.getTotalSpent() ?: BigDecimal.ZERO,
                        purchaseCount = row.getPurchaseCount(),
                    )
            }
    }

    fun getVariantSummaries(
        accountId: UUID,
        variantIds: Collection<UUID>,
    ): Map<UUID, VariantPriceSummary> {
        if (variantIds.isEmpty()) return emptyMap()
        return purchaseItemEntityRepository.summarizeVariants(accountId, variantIds)
            .associate { row ->
                row.getVariantId() to
                    VariantPriceSummary(
                        variantId = row.getVariantId(),
                        averageUnitPrice = row.getAverageUnitPrice(),
                    )
            }
    }

    fun getPriceHistory(
        accountId: UUID,
        productId: UUID,
        variantId: UUID?,
        startDate: LocalDate?,
        endDate: LocalDate?,
    ): List<PricePoint> {
        requireNotNull(productRepository.findById(productId, accountId)) { "Product not found" }
        if (variantId != null) {
            val variant =
                requireNotNull(productVariantRepository.findById(variantId, accountId)) { "Variant not found" }
            require(variant.productId == productId) { "Variant does not belong to product" }
        }
        return purchaseItemEntityRepository.findPriceHistory(accountId, productId, variantId, startDate, endDate)
            .map { row ->
                PricePoint(
                    date = row.getDate(),
                    unitPrice = row.getUnitPrice(),
                    storeName = row.getStoreName(),
                    municipality = row.getMunicipality(),
                    state = row.getState(),
                )
            }
    }

    fun getConsumptionTrend(
        accountId: UUID,
        productId: UUID,
        periodMonths: Int = 6,
    ): ConsumptionTrend {
        requireNotNull(productRepository.findById(productId, accountId)) { "Product not found" }
        val normalizedMonths = periodMonths.coerceIn(1, 24)
        val currentMonth = YearMonth.now()
        val startMonth = currentMonth.minusMonths((normalizedMonths - 1).toLong())
        val startDate = startMonth.atDay(1)

        val pointsByMonth =
            purchaseItemEntityRepository.findConsumptionTrend(accountId, productId, startDate)
                .associateBy { YearMonth.from(it.getMonth()) }

        val months =
            buildList {
                var cursor = startMonth
                repeat(normalizedMonths) {
                    val row = pointsByMonth[cursor]
                    add(
                        ConsumptionTrendPoint(
                            month = cursor.atDay(1),
                            totalQuantity = row?.getTotalQuantity() ?: BigDecimal.ZERO,
                            totalSpent = row?.getTotalSpent() ?: BigDecimal.ZERO,
                            purchaseCount = row?.getPurchaseCount() ?: 0L,
                        ),
                    )
                    cursor = cursor.plusMonths(1)
                }
            }

        return ConsumptionTrend(
            productId = productId,
            periodMonths = normalizedMonths,
            totalQuantity = months.fold(BigDecimal.ZERO) { acc, point -> acc + point.totalQuantity },
            totalSpent = months.fold(BigDecimal.ZERO) { acc, point -> acc + point.totalSpent },
            purchaseCount = months.sumOf { it.purchaseCount },
            months = months,
        )
    }

    fun getStorePriceComparisons(
        accountId: UUID,
        productId: UUID,
        startDate: LocalDate?,
        endDate: LocalDate?,
    ): List<StorePriceComparison> {
        requireNotNull(productRepository.findById(productId, accountId)) { "Product not found" }
        return purchaseItemEntityRepository.findStorePriceComparisons(accountId, productId, startDate, endDate)
            .map { row ->
                StorePriceComparison(
                    storeName = row.getStoreName(),
                    municipality = row.getMunicipality(),
                    state = row.getState(),
                    averageUnitPrice = row.getAverageUnitPrice(),
                    purchaseCount = row.getPurchaseCount(),
                    lastPurchaseDate = row.getLastPurchaseDate(),
                )
            }
    }

    fun getSpendingByNcm(
        accountId: UUID,
        depth: Int = 4,
        startDate: LocalDate?,
        endDate: LocalDate?,
    ): List<NcmSpending> {
        require(depth in setOf(2, 4, 6, 8)) { "depth must be one of 2, 4, 6, 8" }
        val rows = purchaseItemEntityRepository.findSpendingByNcm(accountId, depth, startDate, endDate)
        return rows.groupBy { it.getNcmCode() }
            .map { (ncmCode, groupedRows) ->
                val months =
                    groupedRows
                        .sortedBy { it.getMonth() }
                        .map { row ->
                            NcmMonthlySpending(
                                month = row.getMonth(),
                                totalSpent = row.getTotalSpent() ?: BigDecimal.ZERO,
                                itemCount = row.getItemCount(),
                            )
                        }
                NcmSpending(
                    ncmCode = ncmCode,
                    ncmDescription = groupedRows.firstOrNull()?.getNcmDescription(),
                    totalSpent = months.fold(BigDecimal.ZERO) { acc, month -> acc + month.totalSpent },
                    itemCount = months.sumOf { it.itemCount },
                    months = months,
                )
            }
            .sortedByDescending { it.totalSpent }
    }
}
