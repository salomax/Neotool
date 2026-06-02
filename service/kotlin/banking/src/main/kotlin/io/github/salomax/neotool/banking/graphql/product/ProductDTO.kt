package io.github.salomax.neotool.banking.graphql.product

import io.github.salomax.neotool.banking.domain.import.nfce.ProductAliasSource
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class ProductDTO(
    val id: UUID,
    val name: String,
    val ncmCode: String?,
    val defaultUnit: String?,
    val category: String?,
    val variants: List<ProductVariantDTO>,
    val averagePrice: Double?,
    val totalSpent: Double,
    val purchaseCount: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class ProductVariantDTO(
    val id: UUID,
    val productId: UUID,
    val name: String,
    val brand: String?,
    val ean: String?,
    val quantityValue: Double?,
    val quantityUnit: String?,
    val normalizedQuantityValue: Double?,
    val normalizedQuantityUnit: String?,
    val averageUnitPrice: Double?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class ProductAliasDTO(
    val id: UUID,
    val productVariantId: UUID,
    val productVariant: ProductVariantDTO?,
    val rawDescription: String,
    val source: ProductAliasSource,
    val confidence: Double?,
    val confirmed: Boolean,
    val createdAt: Instant,
)

data class PurchaseItemDTO(
    val id: UUID,
    val transactionId: UUID,
    val productVariantId: UUID?,
    val productVariant: ProductVariantDTO?,
    val sequence: Int,
    val rawDescription: String,
    val ncmCode: String?,
    val ean: String?,
    val issuerProductCode: String?,
    val quantity: Double,
    val unit: String,
    val unitPrice: Double,
    val totalPrice: Double,
    val discount: Double?,
    val effectivePrice: Double,
    val normalizedUnitPrice: Double?,
    val taxAmount: Double?,
    val createdAt: Instant,
)

data class NfceDocumentDTO(
    val id: UUID,
    val transactionId: UUID,
    val accessKey: String,
    val issuerCnpj: String,
    val issuerName: String,
    val issuerTradeName: String?,
    val issuerStateRegistration: String?,
    val issuerAddress: String?,
    val issuerPostalCode: String?,
    val emissionDate: Instant,
    val totalValue: Double,
    val totalTax: Double?,
    val buyerCpf: String?,
    val municipalityCode: String?,
    val municipality: String?,
    val state: String,
    val countryCode: String,
    val sourceUrl: String?,
    val items: List<PurchaseItemDTO>,
    val createdAt: Instant,
)

data class UnmappedItemGroupDTO(
    val rawDescription: String,
    val count: Int,
    val suggestedVariant: ProductVariantDTO?,
    val confidence: Float?,
    val sampleItems: List<PurchaseItemDTO>,
)

data class PricePointDTO(
    val date: LocalDate,
    val unitPrice: Double,
    val storeName: String?,
    val municipality: String?,
    val state: String?,
)

data class ConsumptionTrendPointDTO(
    val month: LocalDate,
    val totalQuantity: Double,
    val totalSpent: Double,
    val purchaseCount: Int,
)

data class ConsumptionTrendDTO(
    val productId: UUID,
    val periodMonths: Int,
    val totalQuantity: Double,
    val totalSpent: Double,
    val purchaseCount: Int,
    val months: List<ConsumptionTrendPointDTO>,
)

data class StorePriceComparisonDTO(
    val storeName: String?,
    val municipality: String?,
    val state: String?,
    val averageUnitPrice: Double?,
    val purchaseCount: Int,
    val lastPurchaseDate: LocalDate?,
)

data class NcmMonthlySpendingDTO(
    val month: LocalDate,
    val totalSpent: Double,
    val itemCount: Int,
)

data class NcmSpendingDTO(
    val ncmCode: String,
    val ncmDescription: String?,
    val totalSpent: Double,
    val itemCount: Int,
    val months: List<NcmMonthlySpendingDTO>,
)
